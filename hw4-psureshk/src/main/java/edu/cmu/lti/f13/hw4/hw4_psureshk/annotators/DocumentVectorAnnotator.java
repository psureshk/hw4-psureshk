package edu.cmu.lti.f13.hw4.hw4_psureshk.annotators;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import edu.stanford.nlp.dcoref.CorefChain;
import edu.stanford.nlp.dcoref.CorefCoreAnnotations.CorefChainAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.PartOfSpeechAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.SentencesAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TokensAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphCoreAnnotations.CollapsedCCProcessedDependenciesAnnotation;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeCoreAnnotations.TreeAnnotation;
import edu.stanford.nlp.util.CoreMap;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.jcas.JCas;
import org.uimafit.util.FSCollectionFactory;

import edu.cmu.lti.f13.hw4.hw4_psureshk.VectorSpaceRetrieval;
import edu.cmu.lti.f13.hw4.hw4_psureshk.typesystems.Document;
import edu.cmu.lti.f13.hw4.hw4_psureshk.typesystems.Token;
import edu.cmu.lti.f13.hw4.hw4_psureshk.utils.Utils;
import edu.stanford.nlp.ling.CoreAnnotations.LemmaAnnotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;

public class DocumentVectorAnnotator extends JCasAnnotator_ImplBase {

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {

    FSIterator<org.apache.uima.jcas.tcas.Annotation> iter = jcas.getAnnotationIndex().iterator();
    if (iter.isValid()) {
      iter.moveToNext();
      Document doc = (Document) iter.get();
      try {
        createTermFreqVector(jcas, doc);
      } catch (IOException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }

  }

  /**
   * 
   * @param jcas
   * @param doc
   * @throws IOException
   *           This function tokenizes the input document (one sentence) into tokens. These tokens
   *           are verified if they are one among the stopwords. If not, then the frequency is
   *           measured by adding the token and number of occurences of the token. HashMap is used
   *           to effectively store and retrieve the frequency.
   */

  private void createTermFreqVector(JCas jcas, Document doc) throws IOException {

    String docText = doc.getText();

    // TO DO: construct a vector of tokens and update the tokenList in CAS
    String tokens[] = docText.toLowerCase().split(" ");// replaceAll("[,.']", "").

    Properties props = new Properties();
    props.put("annotators", "tokenize, ssplit, pos, lemma, ner, parse, dcoref");
    // create an empty Annotation just with the given text
    Annotation document = new Annotation(docText);
    StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
    // run all Annotators on this text
    pipeline.annotate(document);

    List<CoreMap> sentences = document.get(SentencesAnnotation.class);
    ArrayList<String> lemmaverb = new ArrayList<String>();
    for (CoreMap sentence : sentences) {
      // traversing the words in the current sentence
      // a CoreLabel is a CoreMap with additional token-specific methods
      for (CoreLabel token : sentence.get(TokensAnnotation.class)) {
        lemmaverb.add(token.get(LemmaAnnotation.class));
      }
    }

    // Loading stopwords into array list
    URL docUrl = VectorSpaceRetrieval.class.getResource("/stopwords.txt");
    if (docUrl == null) {
      throw new IllegalArgumentException("Error opening stopwords.txt");
    }
    BufferedReader br = new BufferedReader(new InputStreamReader(docUrl.openStream()));
    String sLine;
    ArrayList<String> stopwords;
    try {
      stopwords = new ArrayList<String>();
      while ((sLine = br.readLine()) != null) {
        stopwords.add(sLine);
      }
      br.close();
      // Checking if the token is one among the stopwords
      Map<String, Integer> tokenFreqMap = new HashMap<String, Integer>();
      for (int i = 0; i < tokens.length; i++) {
        if (!stopwords.contains(tokens[i])) {
          if (tokenFreqMap.containsKey(tokens[i])) {
            tokenFreqMap.put(lemmaverb.get(i), tokenFreqMap.get(tokens[i]).intValue() + 1);
          } else
            tokenFreqMap.put(lemmaverb.get(i), 1);
        }
      }

      // The FrequencyMap is being added to the arrayList so that it can be converted to
      // fslist and updated in the Document typesystem
      ArrayList<Token> al = new ArrayList<Token>();
      Token t;
      Iterator it = tokenFreqMap.entrySet().iterator();
      while (it.hasNext()) {
        Map.Entry pairs = (Map.Entry) it.next();
        t = new Token(jcas);
        t.setText(pairs.getKey().toString());
        t.setFrequency((Integer) pairs.getValue());
        al.add(t);
      }
      org.apache.uima.jcas.cas.FSList tfsList = Utils.fromCollectionToFSList(jcas, al);
      doc.setTokenList(tfsList);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
