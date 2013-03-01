package si.zitnik.research.sna.software

import util.{DatasetWriter, SoftwareFileUtil, SourceFinder}
import si.zitnik.research.sna.software.enum.SourceLocations
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
object CommentExtractor extends Logging {

  private def extractComments(dataset: SourceLocations.Value) {
    val dsName = dataset.toString

    logger.info("Doing project: %s".format(dsName))
    val allSources = SourceFinder.findFiles(dsName)
    val datasetValues = ArrayBuffer[String]()

    allSources.foreach(filename => {
      //println(filename)
      val fileSource = Source.fromFile(filename, "latin1").getLines().mkString("\n") //latin1 does not have invalid codes

      var comments = SoftwareFileUtil.extractComments(fileSource).trim.replaceAll("<.*?>", "")
      val className = "%s.%s".format(SoftwareFileUtil.extractPackage(fileSource), SoftwareFileUtil.extractClassName(filename))

      //println(comments)

      datasetValues += "%s \"%s\"".format(className, comments)
    })

    DatasetWriter.writeLines("result/COMMENTS_%s.txt".format(dsName.replaceFirst(SourceLocations.location, "").replaceAll("/.*", "")), datasetValues, "#CANONICAL_CLASS_NAME \"AUTHOR\"")

  }


  def main(args: Array[String]) {
    extractComments(SourceLocations.VUZE_4901_02)
    extractComments(SourceLocations.MIKIOBRAUN_JBLAS_6668AC9)
    extractComments(SourceLocations.LUCENE_4_1_0)
    extractComments(SourceLocations.COLT)
    extractComments(SourceLocations.HADOOP_2_0_3_alpha)
    extractComments(SourceLocations.JBULLET_20101010)
    extractComments(SourceLocations.JUNG2_2_0_1)
    extractComments(SourceLocations.JDK_1_8_0)
  }

}
