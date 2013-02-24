package si.zitnik.research.sna.software.enum

/**
 * Created with IntelliJ IDEA.
 * User: slavkoz
 * Date: 2/24/13
 * Time: 6:39 PM
 * To change this template use File | Settings | File Templates.
 */
object SourceLocations extends Enumeration {
  private val location = "SoftwareSources/"

  val VUZE_4901_02 = Value(location + "Vuze_4901-02_source")
  val MIKIOBRAUN_JBLAS_6668AC9 = Value(location + "mikiobraun-jblas-6668ac9/src/main/java")
  val LUCENE_4_1_0 = Value(location + "lucene-4.1.0/core/src/java")
  val COLT = Value(location + "colt/src")
  val HADOOP_2_0_3_alpha = Value(location + "hadoop-2.0.3-alpha/share/hadoop/common/sources/hadoop-common-2.0.3-alpha-sources")
  val JBULLET_20101010 = Value(location + "jbullet-20101010/src")
  val JUNG2_2_0_1 = Value(location + "jung2-2_0_1-sources/all_sources")
  val JDK_1_8_0 = Value(location + "jdk1.8.0/src")
}
