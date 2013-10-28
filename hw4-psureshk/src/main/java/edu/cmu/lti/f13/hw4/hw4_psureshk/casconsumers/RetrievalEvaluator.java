package edu.cmu.lti.f13.hw4.hw4_psureshk.casconsumers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.collection.CasConsumer_ImplBase;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceProcessException;
import org.apache.uima.util.ProcessTrace;
import org.apache.uima.cas.text.AnnotationIndex;

import edu.cmu.lti.f13.hw4.hw4_psureshk.typesystems.Document;
import edu.cmu.lti.f13.hw4.hw4_psureshk.typesystems.Token;
import edu.cmu.lti.f13.hw4.hw4_psureshk.utils.Utils;

public class RetrievalEvaluator extends CasConsumer_ImplBase {

  /** query id number **/
  public ArrayList<Integer> qIdList;

  /** query and text relevant values **/
  public ArrayList<Integer> relList;

  /** hashmap of every sentence consisting of token=frequency pair **/
  public ArrayList al;

  /** ArrayList to store Ranks **/
  public ArrayList<Integer> rankList;

  /** ArrayList to store sentences for printing purpose **/
  public ArrayList<String> sentList;

  public void initialize() throws ResourceInitializationException {

    qIdList = new ArrayList<Integer>();
    relList = new ArrayList<Integer>();
    al = new ArrayList();
    rankList = new ArrayList<Integer>();
    sentList = new ArrayList<String>();
  }

  /**
   * This function gathers the details like QueryId, RelevanceValue and the HashMap of every
   * sentence consisting of the token=frequency pair and adds them to the appropriate List mentioned
   * above
   */
  @Override
  public void processCas(CAS aCas) throws ResourceProcessException {

    JCas jcas;
    try {
      jcas = aCas.getJCas();
    } catch (CASException e) {
      throw new ResourceProcessException(e);
    }

    FSIterator it = jcas.getAnnotationIndex(Document.type).iterator();

    if (it.hasNext()) {
      Document doc = (Document) it.next();
      sentList.add(doc.getText());
      // Make sure that your previous annotators have populated this in CAS
      FSList fsTokenList = doc.getTokenList();
      ArrayList<Token> tokenList = Utils.fromFSListToCollection(fsTokenList, Token.class);
      // al = Utils.fromFSListToCollection(fsTokenList, Token.class);
      qIdList.add(doc.getQueryID());
      relList.add(doc.getRelevanceValue());
      Map<String, Integer> tokenFreqMap = new HashMap<String, Integer>();
      for (int i = 0; i < tokenList.size(); i++) {
        Token t = (Token) tokenList.get(i);
        tokenFreqMap.put(t.getText(), t.getFrequency());
      }
      al.add(tokenFreqMap);
      // Do something useful here

    }

  }

  /**
   * This function is called once the information about all the sentences in the document are
   * gathered.
   * 
   * This function performs the Cosine Similarity, Jaccard Coefficient , Dice Coefficient and MRR
   * calculation.
   * 
   * From the gathered lists of queryId, relevance value and token-freq map, the query vector and
   * document vector are separated based on the relevance value. If relevance value = 99 then it is
   * the query vector. Else, document vector. The Query Vector and Document vector are passed as
   * inputs to the Cosine Similarity, Jaccard Coefficient and Dice COefficient functions.
   * 
   * The scores returned are stored in an ArrayList to calculate the rank. The scores are sorted in
   * descending order and the position corresponding to the score whose relevance value = 1 is the
   * rank.
   * 
   * The rank value per query is stored in a list and is used for calculating the MRR.
   */
  @Override
  public void collectionProcessComplete(ProcessTrace arg0) throws ResourceProcessException,
          IOException {

    super.collectionProcessComplete(arg0);
    // Cosine Similarity
    try {
      int queryId = 0;
      boolean flag = false;
      Map<String, Integer> queryVector = new HashMap<String, Integer>();
      Map<String, Integer> docVector = new HashMap<String, Integer>();
      ArrayList<Double> docScore = new ArrayList<Double>();
      Map<Double,Integer> mapScore = new HashMap<Double,Integer>();
      double SuccessScore = 0.0;
      int SuccessIndex = -1;
      System.out.println("Cosine Similarity");
      for (int i = 0; i < qIdList.size(); i++) {
        if (relList.get(i) == 99) {
          queryVector = (Map<String, Integer>) al.get(i);
          if (queryId != qIdList.get(i) && queryId != 0) {
            Collections.sort(docScore, Collections.reverseOrder());// descending order sort
            for (int j = 0; j < docScore.size(); j++) {
              if (SuccessScore == docScore.get(j)) {
                if (!flag) {
                  flag = true;
                  System.out.println("Score: " + SuccessScore + "\trank=" + (j + 1) + "\trel=1 "
                          + "qid=" + queryId + " " + sentList.get(SuccessIndex));
                  rankList.add(j + 1);
                }
              }
            }
            docScore = new ArrayList<Double>();
            SuccessScore = 0.0;
            SuccessIndex = -1;
            flag = false;
            docVector = null;
          }
          queryId = qIdList.get(i);
          continue;
        } else {
          if (queryId == qIdList.get(i)) {
            docVector = (Map) al.get(i);
          }
        }
        if (queryVector != null && docVector != null) {
          double score = computeCosineSimilarity(queryVector, docVector);
          if (queryId == qIdList.get(i)) {
            if (relList.get(i) == 1) {
              SuccessIndex = i;
              SuccessScore = score;
            }
            docScore.add(score);
          }
        }

      }
      Collections.sort(docScore, Collections.reverseOrder());// descending order sort
      for (int j = 0; j < docScore.size(); j++) {
        if (SuccessScore == docScore.get(j)) {
          if (!flag) {
            flag = true;
            System.out.println("Score: " + SuccessScore+ "\trank=" + (j + 1) + "\trel=1 " + "qid="
                    + queryId + " " + sentList.get(SuccessIndex));
            rankList.add(j + 1);
          }
        }
      }
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    // cosine similarity measure ends

    double metric_mrr = compute_mrr();
    System.out.println(" (MRR) Mean Reciprocal Rank ::" + metric_mrr);

    rankList = new ArrayList<Integer>();

    // Jaccard Coefficient
    try {
      int queryId = 0;
      boolean flag = false;
      Map<String, Integer> queryVector = new HashMap<String, Integer>();
      Map<String, Integer> docVector = new HashMap<String, Integer>();
      ArrayList<Double> docScore = new ArrayList<Double>();
      double SuccessScore = 0.0;
      int SuccessIndex = -1;
      System.out.println("Jaccard Coefficient");
      for (int i = 0; i < qIdList.size(); i++) {
        if (relList.get(i) == 99) {
          queryVector = (Map<String, Integer>) al.get(i);
          if (queryId != qIdList.get(i) && queryId != 0) {
            Collections.sort(docScore, Collections.reverseOrder());// descending order sort
            for (int j = 0; j < docScore.size(); j++) {
              if (SuccessScore == docScore.get(j)) {
                if (!flag) {
                  flag = true;
                  System.out.println("Score: " + SuccessScore + "\trank=" + (j + 1) + "\trel=1 "
                          + "qid=" + queryId + " " + sentList.get(SuccessIndex));
                  rankList.add(j + 1);
                }
              }
            }
            docScore = new ArrayList<Double>();
            SuccessScore = 0.0;
            SuccessIndex = -1;
            flag = false;
            docVector = null;
          }
          queryId = qIdList.get(i);
          continue;
        } else {
          if (queryId == qIdList.get(i)) {
            docVector = (Map) al.get(i);
          }
        }
        if (queryVector != null && docVector != null) {
          double score = computeJaccardCoefficient(queryVector, docVector);
          if (queryId == qIdList.get(i)) {
            if (relList.get(i) == 1) {
              SuccessIndex = i;
              SuccessScore = score;
            }
            docScore.add(score);
          }
        }

      }
      Collections.sort(docScore, Collections.reverseOrder());// descending order sort
      for (int j = 0; j < docScore.size(); j++) {
        if (SuccessScore == docScore.get(j)) {
          if (!flag) {
            flag = true;
            System.out.println("Score: " + SuccessScore + "\trank=" + (j + 1) + "\trel=1 " + "qid="
                    + queryId + " " + sentList.get(SuccessIndex));
            rankList.add(j + 1);
          }
        }
      }
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    // jaccard coefficient measure ends

    // TODO :: compute the metric:: mean reciprocal rank
    metric_mrr = compute_mrr();
    System.out.println(" (MRR) Mean Reciprocal Rank ::" + metric_mrr);

    rankList = new ArrayList<Integer>();

    // Dice Coefficient
    try {
      int queryId = 0;
      boolean flag = false;
      Map<String, Integer> queryVector = new HashMap<String, Integer>();
      Map<String, Integer> docVector = new HashMap<String, Integer>();
      ArrayList<Double> docScore = new ArrayList<Double>();
      double SuccessScore = 0.0;
      int SuccessIndex = -1;
      System.out.println("Dice Coefficient");
      for (int i = 0; i < qIdList.size(); i++) {
        if (relList.get(i) == 99) {
          queryVector = (Map<String, Integer>) al.get(i);
          if (queryId != qIdList.get(i) && queryId != 0) {
            Collections.sort(docScore, Collections.reverseOrder());// descending order sort
            for (int j = 0; j < docScore.size(); j++) {
              if (SuccessScore == docScore.get(j)) {
                if (!flag) {
                  flag=true;
                  System.out.println("Score: " + SuccessScore + "\trank=" + (j + 1) + "\trel=1 "
                          + "qid=" + queryId + " " + sentList.get(SuccessIndex));
                  rankList.add(j + 1);
                }
              }
            }
            docScore = new ArrayList<Double>();
            SuccessScore = 0.0;
            SuccessIndex = -1;
            flag = false;
            docVector = null;
          }
          queryId = qIdList.get(i);
          continue;
        } else {
          if (queryId == qIdList.get(i)) {
            docVector = (Map) al.get(i);
          }
        }
        if (queryVector != null && docVector != null) {
          double score = computeDiceCoefficient(queryVector, docVector);
          if (queryId == qIdList.get(i)) {
            if (relList.get(i) == 1) {
              SuccessIndex = i;
              SuccessScore = score;
            }
            docScore.add(score);
          }
        }

      }
      Collections.sort(docScore, Collections.reverseOrder());// descending order sort
      for (int j = 0; j < docScore.size(); j++) {
        if (SuccessScore == docScore.get(j)) {
          if (!flag) {
            flag =true;
            System.out.println("Score: " + SuccessScore + "\trank=" + (j + 1) + "\trel=1 " + "qid="
                    + queryId + " " + sentList.get(SuccessIndex));
            rankList.add(j + 1);
          }
        }
      }
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
    // dice coefficient measure ends

    // TODO :: compute the metric:: mean reciprocal rank
    metric_mrr = compute_mrr();
    System.out.println(" (MRR) Mean Reciprocal Rank ::" + metric_mrr);

  }

  /**
   * Numerator = product of common words in the question and in the answer
   * Denominator = product of square root of (frequency of words in question and 
   * frequency of words in the answer) 
   * @param queryVector
   * @param docVector
   * @return
   */
  private double computeCosineSimilarity(Map<String, Integer> queryVector,
          Map<String, Integer> docVector) {
    double cosine_similarity = 0.0;
    double query = 0.0;
    double docum = 0.0;
    // TODO :: compute cosine similarity between two sentences
    Iterator it = queryVector.entrySet().iterator();
    double numerator = 0.0;
    while (it.hasNext()) {
      Map.Entry pairs = (Map.Entry) it.next();
      String key = (String) pairs.getKey();
      query += ((Integer) queryVector.get(key)).intValue()
              * ((Integer) queryVector.get(key)).intValue();
      if (docVector.containsKey(key))
        numerator += ((Integer) docVector.get(key)).intValue()
                * ((Integer) queryVector.get(key)).intValue();
    }
    Iterator it1 = docVector.entrySet().iterator();
    while (it1.hasNext()) {
      Map.Entry pairs = (Map.Entry) it1.next();
      String key = (String) pairs.getKey();
      docum += ((Integer) docVector.get(key)).intValue()
              * ((Integer) docVector.get(key)).intValue();

    }
    cosine_similarity = numerator / Math.sqrt(query) / Math.sqrt(docum);
    return cosine_similarity;
  }
/**
 * The jaccard score is as simple as getting the frequency of common words in question 
 * and answer divided by the union of words in question and in the answer.
 * @param queryVector
 * @param docVector
 * @return
 */
  private double computeJaccardCoefficient(Map<String, Integer> queryVector,
          Map<String, Integer> docVector) {
    double jaccard_Score = 0.0;
    double query = 0.0;
    double docum = 0.0;
    // TODO :: compute cosine similarity between two sentences
    Iterator it = queryVector.entrySet().iterator();
    double query_docum_common = 0.0;
    while (it.hasNext()) {
      Map.Entry pairs = (Map.Entry) it.next();
      String key = (String) pairs.getKey();
      query += ((Integer) queryVector.get(key)).intValue()
              * ((Integer) queryVector.get(key)).intValue();
      if (docVector.containsKey(key))
        query_docum_common += ((Integer) docVector.get(key)).intValue()
                * ((Integer) queryVector.get(key)).intValue();
    }
    Iterator it1 = docVector.entrySet().iterator();
    while (it1.hasNext()) {
      Map.Entry pairs = (Map.Entry) it1.next();
      String key = (String) pairs.getKey();
      docum += ((Integer) docVector.get(key)).intValue()
              * ((Integer) docVector.get(key)).intValue();

    }
    jaccard_Score = query_docum_common
            / (Math.sqrt(query) + Math.sqrt(docum) - Math.sqrt(query_docum_common));
    return jaccard_Score;
  }
/**
 * The dice score is as simple as getting twice the frequency of common words in question 
 * and answer divided by the union of words in question and 
 * in the answer + the common words in question and answer.
 * @param queryVector
 * @param docVector
 * @return
 */
  private double computeDiceCoefficient(Map<String, Integer> queryVector,
          Map<String, Integer> docVector) {
    double dice_Score = 0.0;
    double query = 0.0;
    double docum = 0.0;
    // TODO :: compute cosine similarity between two sentences
    Iterator it = queryVector.entrySet().iterator();
    double query_docum_common = 0.0;
    while (it.hasNext()) {
      Map.Entry pairs = (Map.Entry) it.next();
      String key = (String) pairs.getKey();
      query += ((Integer) queryVector.get(key)).intValue()
              * ((Integer) queryVector.get(key)).intValue();
      if (docVector.containsKey(key))
        query_docum_common += ((Integer) docVector.get(key)).intValue()
                * ((Integer) queryVector.get(key)).intValue();
    }
    Iterator it1 = docVector.entrySet().iterator();
    while (it1.hasNext()) {
      Map.Entry pairs = (Map.Entry) it1.next();
      String key = (String) pairs.getKey();
      docum += ((Integer) docVector.get(key)).intValue()
              * ((Integer) docVector.get(key)).intValue();

    }
    dice_Score = (2 * query_docum_common) / (Math.sqrt(query) + Math.sqrt(docum));
    return dice_Score;
  }

  /**
   * Mean Reciprocal Rank - the sum of the reciprocal of all the ranks is calculated 
   * and is divided by the number of entries in the rankList.
   * @return mrr
   */
  private double compute_mrr() {
    double metric_mrr = 0.0;
    double sum = 0.0;
    // TODO :: compute Mean Reciprocal Rank (MRR) of the text collection
    for (int i = 0; i < rankList.size(); i++) {
      sum += (1.0 / ((double) rankList.get(i)));
    }
    metric_mrr = sum / rankList.size();
    return metric_mrr;
  }

}
