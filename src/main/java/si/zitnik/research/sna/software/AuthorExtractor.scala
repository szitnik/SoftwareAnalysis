package si.zitnik.research.sna.software

import network.NetworkBuilder
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
    val datasetValues = ArrayBuffer[(String, String)]()
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
        if (author.equals("the JUNG Project and the Regents of the University")) {
          author = "UNKNOWN"
        }
      } else if (dataset.equals(SourceLocations.JDK_1_8_0)) {
        if (author.equals("2011") || author.equals("2012")) {
          author = "UNKNOWN"
        }
        if (className.equals("javax.xml.bind.util.JAXBSource") ||
            className.equals("javax.xml.bind.util.JAXBResult") ||
            className.equals("javax.xml.bind.SchemaOutputResolver") ||
          className.equals("javax.xml.bind.helpers.AbstractUnmarshallerImpl")) {
          author = "Kohsuke Kawaguchi"
        }
        if (className.equals("javax.xml.bind.Binder")) {
          author = "Kohsuke Kawaguchi|Joseph Fialli"
        }
      } else if (dataset.equals(SourceLocations.LUCENE_4_1_0)) {
        if (author.equals("may not be used to endorse or promote products")) {
          author = "Anders Moeller"
        }
      }

      if (author.equals("\"")) {
        author = "UNKNOWN"
      }

      datasetValues += ((className, author))
      authors.add(author)

      if (author.equals("UNKNOWN")) {
        //println(filename)
        unknownCounter += 1
      }
    })

    val networkValues = NetworkBuilder.buildNetworkFulltextMatch(datasetValues)
    DatasetWriter.writeLines(
      "result/NETWORK_AUTHORS_%s.txt".format(dsName.replaceFirst(SourceLocations.location, "").replaceAll("/.*", "")),
      networkValues.map(v => "%s %s".format(v._1, v._2)),
      "#CANONICAL_CLASS_NAME CANONICAL_CLASS_NAME")
    logger.info("\tNetwork: %d connections".format(networkValues.size))

    DatasetWriter.writeLines(
      "result/AUTHORS_%s.txt".format(dsName.replaceFirst(SourceLocations.location, "").replaceAll("/.*", "")),
      datasetValues.map(v => "%s \"%s\"".format(v._1, v._2)),
      "#CANONICAL_CLASS_NAME \"AUTHOR\"")

    logger.info("\tUnknown author classes: %d".format(unknownCounter))
    logger.info("\tAll classes: %d".format(datasetValues.size))
    logger.info("\tDistinct authors: %d, %s".format(authors.size, authors.mkString("[",",","]")))
  }


  def main(args: Array[String]) {
    extractAuthors(SourceLocations.VUZE_4901_02)
    extractAuthors(SourceLocations.MIKIOBRAUN_JBLAS_6668AC9)
    extractAuthors(SourceLocations.LUCENE_4_1_0)
    extractAuthors(SourceLocations.COLT)
    extractAuthors(SourceLocations.HADOOP_2_0_3_alpha)
    extractAuthors(SourceLocations.JBULLET_20101010) //Martin Dvorak is possibly jezek as has mail jezek2.
    extractAuthors(SourceLocations.JUNG2_2_0_1)
    extractAuthors(SourceLocations.JDK_1_8_0)
  }

}
