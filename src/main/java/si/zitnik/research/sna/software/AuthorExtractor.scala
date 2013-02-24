package si.zitnik.research.sna.software

import util.{DatasetWriter, SoftwareFileUtil, SourceFinder}
import si.zitnik.research.sna.software.enum.SourceLocations
import scala.util.matching.Regex
import io.Source
import collection.mutable.ArrayBuffer
import com.typesafe.scalalogging.slf4j.Logging
import collection.mutable

/**
 * Created with IntelliJ IDEA.
 * User: slavkoz
 * Date: 2/24/13
 * Time: 6:38 PM
 * To change this template use File | Settings | File Templates.
 */
object AuthorExtractor extends Logging {

  private def extractAuthors(dataset: SourceLocations.Value) {
    val dsName = dataset.toString

    logger.info("Doing project: %s".format(dsName))
    val allSources = SourceFinder.findFiles(dsName)
    var unknownCounter = 0
    val datasetValues = ArrayBuffer[String]()
    val authors = mutable.HashSet[String]()

    allSources.foreach(filename => {
      //println(filename)
      val fileSource = Source.fromFile(filename, "latin1").getLines().mkString("\n") //latin1 does not have invalid codes
      //println("\t"+SoftwareFileUtil.extractPackage(fileSource))
      //println("\t"+SoftwareFileUtil.extractClassName(filename))
      //println("\t"+SoftwareFileUtil.extractAuthor(fileSource))

      var author = SoftwareFileUtil.extractAuthor(fileSource).trim.replaceAll("<.*?>", "")
      val className = "%s.%s".format(SoftwareFileUtil.extractPackage(fileSource), SoftwareFileUtil.extractClassName(filename))

      //EXCEPTIONS
      if (dataset.equals(SourceLocations.MIKIOBRAUN_JBLAS_6668AC9)) {
        val exceptionSet = Set("org.jblas.Eigen", "org.jblas.SimpleBlas")
        if (exceptionSet.contains(className)) {
          author += "|Nicolas Oury"
        }
        if (author.equals("IntelliJ IDEA.")) {
          author = "mikio"
        }
      } else if (dataset.equals(SourceLocations.JUNG2_2_0_1)) {
        if (authors.equals("the JUNG Project and the Regents of the University")) {
          author = "UNKNOWN"
        }
      }

      if (author.equals("\"")) {
        author = "UNKNOWN"
      }

      datasetValues += "%s \"%s\"".format(className, author)
      authors.add(author)

      if (SoftwareFileUtil.extractAuthor(fileSource).equals("UNKNOWN")) {
        //println(filename)
        unknownCounter += 1
      }
    })

    DatasetWriter.writeLines("result/AUTHORS_%s.txt".format(dsName.replaceAll("/.*", "")), datasetValues, "#CANONICAL_CLASS_NAME \"AUTHOR\"")

    logger.info("\tUnknown author classes: %d".format(unknownCounter))
    logger.info("\tAll classes: %d".format(datasetValues.size))
    logger.info("\tDistinct authors: %d, %s".format(authors.size, authors.mkString("[",",","]")))
  }


  def main(args: Array[String]) {
    extractAuthors(SourceLocations.VUZE_4901_02)
    extractAuthors(SourceLocations.MIKIOBRAUN_JBLAS_6668AC9)
    extractAuthors(SourceLocations.LUCENE_4_1_0) //problem - no authors
    extractAuthors(SourceLocations.COLT)
    extractAuthors(SourceLocations.HADOOP_2_0_3_alpha)
    extractAuthors(SourceLocations.JBULLET_20101010) //Martin Dvorak is possibly jezek as has mail jezek2.
    extractAuthors(SourceLocations.JUNG2_2_0_1)
    extractAuthors(SourceLocations.JDK_1_8_0)
  }

}
