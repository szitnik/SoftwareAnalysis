package si.zitnik.research.sna.software.network

import collection.mutable.ArrayBuffer
import collection.mutable
import breeze.linalg.SparseVector

/**
 * Created with IntelliJ IDEA.
 * User: slavkoz
 * Date: 3/2/13
 * Time: 11:56 AM
 * To change this template use File | Settings | File Templates.
 */
object NetworkBuilder {

  def buildNetworkFulltextMatch(buffer: ArrayBuffer[(String, String)]) = {
    val retVal = ArrayBuffer[(String, String)]()

    val classMap = buffer.toMap
    val classNames = classMap.keySet.toArray

    for {
      i <- 0 until classNames.length
      j <- 0 until i
      classA = classNames(i)
      classB = classNames(j)
    } {
      val textA = classMap(classA)
      val textB = classMap(classB)

      if (!textA.trim.isEmpty && !textB.trim.isEmpty) {
        if (textA.equals(textB)) {
          retVal.append(((classA, classB)))
        }
      }
    }

    retVal
  }

  //Bag of word match, all classes matching i or more times are matched
  def buildNetworkBOW(buffer: ArrayBuffer[(String, String)], minMatches: Int) = {
    val retVal = ArrayBuffer[(String, String)]()

    val classMap = buffer.toMap
    val classNames = classMap.keySet.toArray

    for {
      i <- 0 until classNames.length
      j <- 0 until i
      classA = classNames(i)
      classB = classNames(j)
    } {
      val textA = classMap(classA)
      val textB = classMap(classB)

      if (!textA.trim.isEmpty && !textB.trim.isEmpty) {
        if (classMap(classA).split(" ").toSet.intersect(classMap(classB).split(" ").toSet).size >= minMatches) {
          retVal.append(((classA, classB)))
        }
      }
    }

    retVal
  }


  //Bag of word match, all classes matching by Jaccard score threshold
  def buildNetworkBOWJaccard(buffer: ArrayBuffer[(String, String)], scoreThreshold: Double) = {
    val retVal = ArrayBuffer[(String, String)]()

    val classMap = buffer.toMap
    val classNames = classMap.keySet.toArray

    for {
      i <- 0 until classNames.length
      j <- 0 until i
      classA = classNames(i)
      classB = classNames(j)
    } {
      val textA = classMap(classA)
      val textB = classMap(classB)

      if (!textA.trim.isEmpty && !textB.trim.isEmpty) {
        val setA = textA.split(" ").toSet
        val setB = textB.split(" ").toSet

        val jaccard = setA.intersect(setB).size*1.0/setA.union(setB).size

        if (jaccard >= scoreThreshold) {
          retVal.append(((classA, classB)))
        }
      }
    }

    retVal
  }

  def buildNetworkTFIDFCosine(documents: ArrayBuffer[(String, String)], scoreThreshold: Double) = {
    val retVal = ArrayBuffer[(String, String)]()

    val n = documents.size //number of documents
    val wordToIndex = documents.flatMap(_._2.split(" ")).toSet[String].zipWithIndex.toMap //index of words within word vector
    val wordToDocumentFrequencies = mutable.HashMap[String, Int]()

    //fill wordToDocumentFrequencies
    documents.foreach(document => {
      document._2.split(" ").toSet[String].foreach(word => {
        wordToDocumentFrequencies.put(word, wordToDocumentFrequencies.getOrElse(word, 0)+1)
      })
    })


    val documentVectorCache = mutable.HashMap[String, SparseVector[Double]]()

    val calculateWeightVector = (documentText: String) => {
      val retVal = SparseVector.zeros[Double](wordToIndex.keySet.size)

      val words = documentText.split(" ")

      if (words.size != 0 && !documentText.trim.isEmpty) {
        val counts = words.groupBy(x=>x).mapValues(x=>x.length)
        val maxWordCountInDoc = counts.values.max

        words.foreach(word => {
          val tf = counts(word)*1.0/maxWordCountInDoc
          val idf = math.log(n*1.0/wordToDocumentFrequencies(word))
          retVal(wordToIndex(word)) = tf*idf
        })
      }

      retVal
    }:SparseVector[Double]


    for {
      i <- 0 until documents.size
      j <- 0 until i
      classA = documents(i)._1
      classB = documents(j)._1
      docVectorA = documentVectorCache.getOrElseUpdate(classA, calculateWeightVector(documents(i)._2))
      docVectorB = documentVectorCache.getOrElseUpdate(classB, calculateWeightVector(documents(j)._2))
    } {
      val dot = docVectorA.dot(docVectorB)

      if (dot != 0) {
        val cosine = dot / (docVectorA.norm(2) * docVectorB.norm(2))

        if (cosine >= scoreThreshold) {
          retVal.append(((classA, classB)))
        }
      }
    }

    retVal
  }

}
