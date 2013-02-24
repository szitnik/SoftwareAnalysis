package si.zitnik.research.sna.software.util

import java.nio.file.Files
import java.io.{FilenameFilter, File}
import collection.mutable.ArrayBuffer
import si.zitnik.research.sna.software.model.SoftwareFile

/**
 * Created with IntelliJ IDEA.
 * User: slavkoz
 * Date: 2/24/13
 * Time: 6:38 PM
 * To change this template use File | Settings | File Templates.
 */
object SourceFinder {

  def findFiles(contentRoot: String, fileType: String = ".java"): ArrayBuffer[String] = {
    val retVal = ArrayBuffer[String]()
    val file = new File(contentRoot)

    if (file.isDirectory) {
      val files = file.list()
      for (subFile <- files) {
        retVal ++= findFiles("%s/%s".format(contentRoot,subFile))
      }

    } else if (file.getName().endsWith(fileType)) {
      retVal += contentRoot
    }


    retVal
  }

}
