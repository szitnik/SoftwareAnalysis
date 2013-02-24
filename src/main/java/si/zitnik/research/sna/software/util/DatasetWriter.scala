package si.zitnik.research.sna.software.util

import collection.mutable.ArrayBuffer
import java.io.{FileWriter, File, BufferedWriter}
import java.nio.file._
import java.nio.charset.Charset
import scala.collection.JavaConversions._

/**
 * Created with IntelliJ IDEA.
 * User: slavkoz
 * Date: 2/24/13
 * Time: 9:04 PM
 * To change this template use File | Settings | File Templates.
 */
object DatasetWriter {
  def writeLines(filename: String, lines: ArrayBuffer[String], headerLine: String = "#") {
    //Files.write(Paths.get(filename), lines, Charset.forName("utf-8"), StandardOpenOption.WRITE)
    val bw = new BufferedWriter(new FileWriter(new File(filename)))
    bw.write(headerLine); bw.newLine();
    lines.foreach(v => {bw.write(v); bw.newLine()})
    bw.close()
  }

}
