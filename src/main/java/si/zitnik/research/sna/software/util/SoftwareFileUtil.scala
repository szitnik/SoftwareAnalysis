package si.zitnik.research.sna.software.util

import util.matching.Regex
import com.typesafe.scalalogging.slf4j.{Logger, Logging}
import collection.mutable.ArrayBuffer

/**
 * Created with IntelliJ IDEA.
 * User: slavkoz
 * Date: 2/24/13
 * Time: 7:08 PM
 * To change this template use File | Settings | File Templates.
 */
object SoftwareFileUtil extends Logging {


  def extractPackage(fileSource: String): String = {
    //val r = new Regex(""".*^package\s+(.*)\s*;\s*$.*""", "package")
    val r = new Regex(""".*package\s+(.*)\s*;.*""", "package")

    r.findFirstIn(fileSource) match {
      case Some(r(p)) => p;
      case None => {
        logger.error("No package found for source\n%s".format(fileSource))
        System.exit(-1)
        ""
      }
    }
  }

  /*
  Does not work

  def extractClassName(fileSource: String): String = {
    val r = new Regex(""".*[class|interface]\s+([A-Z][a-zA-Z_$0-9]*).*""", "class")

    r.findFirstIn(fileSource) match {
      case Some(r(p)) => p;
      case None => {
        logger.error("No class name found for source\n%s".format(fileSource))
        System.exit(-1)
        ""
      }
    }
  } */

  def extractClassName(filename: String): String = {
    filename.replaceAll(".*/", "").stripSuffix(".java")
  }

  def extractAuthor(fileSource: String): String = {
    //val r = new Regex(""".*[Created by |@author]\s+([a-zA-Z ]+)$.*""", "author")
    //val r = new Regex(""".*(Java port of Bullet \(c\) 2008|Copyright \(c\) 2009,|Copyright \(c\) 2009-2011,|User:|Created by|author)\s+([a-zA-Z @\.]+).*""", "x","author")
    val r = new Regex(""".*(User:|author)\s+([:<=\">/a-zA-Z @\.0-9]+).*""", "x","author")

    var res = r.findFirstIn(fileSource) match {
      case Some(r(x, p)) => p;
      case None => {
        //logger.error("**********************************\n"*10)
        //logger.error("No author found for source\n%s".format(fileSource))
        //System.exit(-1)
        "UNKNOWN"
      }
    }

    if (res.equals("UNKNOWN")) {
      val r1 = new Regex(""".*(Copyright \(c\) 2001-2009|Java port of Bullet \(c\) 2008|Copyright \(c\) 2009,|Copyright \(c\) 2009-2011,|User:|Created by|author)\s+([:<=\">/a-zA-Z @\.0-9]+).*""", "x","author")

      res = r1.findFirstIn(fileSource) match {
        case Some(r1(x, p)) => p;
        case None => {
          //logger.error("**********************************\n"*10)
          //logger.error("No author found for source\n%s".format(fileSource))
          //System.exit(-1)
          "UNKNOWN"
        }
      }
    }

    res
  }


  def extractComments(fileSource: String): String = {
    val retVal = ArrayBuffer[String]()

    val ignore = (comment: String) => {
      if (comment.startsWith("user:") ||
          comment.startsWith("author") ||
          comment.startsWith("@author") ||
        comment.startsWith("created") ||
        comment.startsWith("@created") ||
        comment.startsWith("copyright") ||
        comment.startsWith("java port of bullet") ||
        comment.startsWith("jblas - light-weight wrapper for atlas and lapack (http://www.jblas.org) copyright") ||
        comment.startsWith("licensed to the apache software foundation")) {
        true
      } else {
        false
      }
    }: Boolean

    val processComment = (comment: String) => {
        val c = comment.toLowerCase().
        replaceAll("\\*", "").
        replaceAll("@param [a-zA-Z]*", "").
        replaceAll("@return [a-zA-Z]*", "").
        replaceAll("@since [a-zA-Z\\.0-9]*", "").
        replaceAll("(?s)\\s*(@author|author|user|created|@created).*$", "").
        replaceAll("--- end license block ---", "").
        replaceAll("--- begin license block ---", "").
        replaceAll("\\s+", " ").
        trim

        if (!ignore(c)) {
          retVal.append(c)
        }
    }

    //block comments
    var r = new Regex("""(?s)/\*(.*?)\*/""", "comment")
    r.findAllMatchIn(fileSource).foreach(m => processComment(m.group("comment")))
    //line
    r = new Regex("""//(.*)""", "comment")
    r.findAllMatchIn(fileSource).foreach(m => processComment(m.group("comment")))

    //println(fileSource)
    //println(retVal.mkString(" "))

    retVal.mkString(" ")
  }


}
