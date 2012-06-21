package daffodil.section5.simple_types

import junit.framework.Assert._
import org.scalatest.junit.JUnit3Suite
import scala.xml._
import daffodil.xml.XMLUtils
import daffodil.xml.XMLUtils._
import daffodil.dsom.Compiler
import daffodil.util._
import daffodil.tdml.DFDLTestSuite
import java.io.File

class TestSimpleTypes extends JUnit3Suite {
  val testDir = "src/daffodil/section5/simple_types/"
  val aa = testDir + "SimpleTypes.tdml"
  val runner = new DFDLTestSuite(new File(aa))
  
  def test_Long1() { runner.runOneTest("Long1") }
  def test_schema_types_5_04() { runner.runOneTest("schema_types_5_04") }
}