package daffodil.dsom

import daffodil.xml.XMLUtils
import daffodil.util._
import scala.xml._
import daffodil.compiler._
import org.scalatest.junit.JUnitSuite
import daffodil.schema.annotation.props.gen._
import daffodil.schema.annotation.props._
import daffodil.util.Misc
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import java.io.FileOutputStream
import java.nio.channels.WritableByteChannel
import java.io.FileWriter
import java.io.File
import java.nio.ByteBuffer
import org.junit.Test
import daffodil.debugger.Debugger

class TestDsomCompiler extends JUnitSuite with Logging {

  val xsd = XMLUtils.XSD_NAMESPACE
  val dfdl = XMLUtils.DFDL_NAMESPACE
  val xsi = XMLUtils.XSI_NAMESPACE
  val example = XMLUtils.EXAMPLE_NAMESPACE

  val dummyGroupRef = Fakes.fakeGroupRef

  def FindValue(collection: Map[String, String], key: String, value: String): Boolean = {
    val found: Boolean = Option(collection.find(x => x._1 == key && x._2 == value)) match {
      case Some(_) => true
      case None => false
    }
    found
  }

  @Test def testHasProps() {
    val testSchema = TestUtils.dfdlTestSchema(
      <dfdl:format ref="tns:daffodilTest1"/>,
      <xs:element name="list" type="tns:example1"/>
      <xs:complexType name="example1">
        <xs:sequence>
          <xs:element name="w" type="xs:int" dfdl:length="1" dfdl:lengthKind="explicit"/>
        </xs:sequence>
      </xs:complexType>)

    val compiler = Compiler()
    val (sset, _) = compiler.frontEnd(testSchema)
    val Seq(schema) = sset.schemas
    val Seq(schemaDoc) = schema.schemaDocuments
    val Seq(declf) = schemaDoc.globalElementDecls
    val decl = declf.forRoot()

    val df = schemaDoc.defaultFormat
    val tnr = df.textNumberRep
    assertEquals(TextNumberRep.Standard, tnr)
    val tnr2 = decl.textNumberRep
    assertEquals(TextNumberRep.Standard, tnr2)
  }

  @Test def testSchemaValidationSubset() {
    val sch: Node = TestUtils.dfdlTestSchema(
      <dfdl:format ref="tns:daffodilTest1"/>,
      <xs:element name="list">
        <xs:complexType>
          <xs:sequence maxOccurs="2">
            <!-- DFDL SUBSET DOESN'T ALLOW MULTIPLE RECURRING SEQUENCE OR CHOICE -->
            <xs:element name="w" type="xsd:int" dfdl:lengthKind="explicit" dfdl:length="{ 1 }"/>
          </xs:sequence>
        </xs:complexType>
      </xs:element>)

    val compiler = Compiler()
    compiler.setCheckAllTopLevel(true)
    val (sset, _) = compiler.frontEnd(sch)
    assertTrue(sset.isError)
    val diagnostics = sset.getDiagnostics
    val msgs = diagnostics.map { _.getMessage }
    val msg = msgs.mkString("\n")
    val hasErrorText = msg.contains("maxOccurs");
    if (!hasErrorText) this.fail("Didn't get expected error. Got: " + msg)
  }

  @Test def testTypeReferentialError() {
    val sch: Node = TestUtils.dfdlTestSchema(
      <dfdl:format ref="tns:daffodilTest1"/>,
      <xs:element name="list" type="typeDoesNotExist"/>)
    val (sset, _) = Compiler().frontEnd(sch)
    assertTrue(sset.isError)
    val msg = sset.getDiagnostics.toString
    val hasErrorText = msg.contains("typeDoesNotExist");
    if (!hasErrorText) this.fail("Didn't get expected error. Got: " + msg)
  }

  @Test def testSchemaValidationPropertyChecking() {
    val s: Node = TestUtils.dfdlTestSchema(
      <dfdl:format ref="tns:daffodilTest1"/>,
      <xs:element name="list">
        <xs:complexType>
          <xs:sequence>
            <xs:element name="w" type="xsd:int" dfdl:byteOrder="invalidValue" dfdl:lengthKind="explicit" dfdl:length="{ 1 }"/>
          </xs:sequence>
        </xs:complexType>
      </xs:element>)
    val compiler = Compiler()
    compiler.setCheckAllTopLevel(true)
    val (sset, _) = Compiler().frontEnd(s)
    sset.isError // forces compilation
    val diags = sset.getDiagnostics
    // diags.foreach { println(_) }
    val msg = diags.toString
    assertTrue(sset.isError)
    val hasErrorText = msg.contains("invalidValue");
    if (!hasErrorText) this.fail("Didn't get expected error. Got: " + msg)
  }

  @Test def test2() {
    val sc = TestUtils.dfdlTestSchema(
      <dfdl:format ref="tns:daffodilTest1"/>,

      <xs:element name="list" type="tns:example1">
        <xs:annotation>
          <xs:appinfo source={ dfdl }>
            <dfdl:element encoding="US-ASCII" alignmentUnits="bytes"/>
          </xs:appinfo>
        </xs:annotation>
      </xs:element>
      <xs:complexType name="example1">
        <xs:sequence dfdl:separator="">
          <xs:element name="w" type="xs:int" dfdl:length="1" dfdl:lengthKind="explicit"/>
        </xs:sequence>
      </xs:complexType>)

    val (sset, _) = Compiler().frontEnd(sc)

    val Seq(schema) = sset.schemas
    val Seq(schemaDoc) = schema.schemaDocuments
    val Seq(declFactory) = schemaDoc.globalElementDecls
    val decl = declFactory.forRoot()
    val Seq(ct) = schemaDoc.globalComplexTypeDefs
    assertEquals("example1", ct.name)

    val fa = decl.formatAnnotation.asInstanceOf[DFDLElement]
    assertEquals(AlignmentUnits.Bytes, fa.alignmentUnits)
    //    fa.alignmentUnits match {
    //      case AlignmentUnits.Bits => println("was bits")
    //      case AlignmentUnits.Bytes => println("was bytes")
    //    }
  }

  /* @Test def testXsomMultifile(){
   
    val parser = new XSOMParser()
    val apf = new DomAnnotationParserFactory()
    parser.setAnnotationParser(apf)

    val inFile = new File(Misc.getRequiredResource("/test/first.xsd"))

    parser.parse(inFile)

    val sset = parser.getResult()
    val sds = parser.getDocuments().toList
    assertTrue(sds.size() >= 2)
  
    // sds.map{sd => println(sd.getSystemId)}
  }*/

  @Test def testSequence1() {
    val testSchema = TestUtils.dfdlTestSchema(
      <dfdl:format ref="tns:daffodilTest1"/>,

      <xs:element name="list" type="tns:example1">
        <xs:annotation>
          <xs:appinfo source={ dfdl }>
            <dfdl:element encoding="US-ASCII" alignmentUnits="bytes"/>
          </xs:appinfo>
        </xs:annotation>
      </xs:element>
      <xs:complexType name="example1">
        <xs:sequence dfdl:separatorPolicy="required" dfdl:separator="">
          <xs:element name="w" type="xs:int" maxOccurs="1" dfdl:lengthKind="explicit" dfdl:length="1" dfdl:occursCountKind="fixed"/>
        </xs:sequence>
      </xs:complexType>)

    val w = Utility.trim(testSchema)

    val (sset, _) = Compiler().frontEnd(w)
    val Seq(schema) = sset.schemas
    val Seq(schemaDoc) = schema.schemaDocuments
    val Seq(decl) = schemaDoc.globalElementDecls
    val Seq(ct) = schemaDoc.globalComplexTypeDefs
    assertEquals("example1", ct.name)

    val mg = ct.forElement(null).modelGroup.asInstanceOf[Sequence]
    assertTrue(mg.isInstanceOf[Sequence])

    val Seq(elem) = mg.groupMembers
    assertTrue(elem.isInstanceOf[LocalElementDecl])
  }

  @Test def test3 {
    val testSchema = XML.load(Misc.getRequiredResource("/test/example-of-most-dfdl-constructs.dfdl.xml"))
    val compiler = Compiler()

    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    // No annotations
    val Seq(ct) = sd.globalComplexTypeDefs

    // Explore global element decl
    val Seq(e1f, e2f, e3f, e4f, e5f) = sd.globalElementDecls // there are 3 factories
    val e1 = e1f.forRoot()
    val e2 = e2f.forRoot()
    val e3 = e3f.forRoot()
    assertEquals(
      ByteOrder.BigEndian.toString().toLowerCase(),
      e1.formatAnnotation.asInstanceOf[DFDLElement].getProperty("byteOrder").toLowerCase())
    val Seq(a1, a2) = e3.annotationObjs // third one has two annotations
    assertTrue(a2.isInstanceOf[DFDLNewVariableInstance]) // second annotation is newVariableInstance
    assertEquals(OccursCountKind.Implicit, a1.asInstanceOf[DFDLElement].occursCountKind)
    val e1ct = e1.immediateType.get.asInstanceOf[LocalComplexTypeDef] // first one has immediate complex type
    // Explore local complex type def
    val seq = e1ct.modelGroup.asInstanceOf[Sequence] //... which is a sequence
    val sfa = seq.formatAnnotation.asInstanceOf[DFDLSequence] //...annotated with...
    assertEquals(YesNo.No, sfa.initiatedContent) // initiatedContent="no"

    val Seq(e1a: DFDLElement) = e1.annotationObjs
    assertEquals("UTF-8", e1a.getProperty("encoding"))

    // Explore global simple type defs
    val Seq(st1, st2, st3, st4) = sd.globalSimpleTypeDefs // there are two.
    val Seq(b1, b2, b3, b4) = st1.forElement(e1).annotationObjs // first one has 4 annotations
    assertEquals(AlignmentUnits.Bytes, b1.asInstanceOf[DFDLSimpleType].alignmentUnits) // first has alignmentUnits
    assertEquals("tns:myVar1", b2.asInstanceOf[DFDLSetVariable].ref) // second is setVariable with a ref
    assertEquals("yadda yadda yadda", b4.asInstanceOf[DFDLAssert].message) // fourth is an assert with yadda message

    // Explore define formats
    val Seq(df1, df2) = sd.defineFormats // there are two
    val def1 = df1.asInstanceOf[DFDLDefineFormat]
    assertEquals("def1", def1.name) // first is named "def1"
    assertEquals(Representation.Text, def1.formatAnnotation.representation) // has representation="text"

    // Explore define variables
    val Seq(dv1, dv2) = sd.defineVariables // there are two
    //assertEquals("2003年08月27日", dv2.asInstanceOf[DFDLDefineVariable].defaultValue) // second has kanji chars in default value

    // Explore define escape schemes
    val Seq(desc1) = sd.defineEscapeSchemes // only one of these
    val es = desc1.escapeScheme.escapeCharacterRaw
    assertEquals("%%", es) // has escapeCharacter="%%" (note: string literals not digested yet, so %% is %%, not %.

    // Explore global group defs
    val Seq(gr1, gr2, gr3, gr4, gr5) = sd.globalGroupDefs // there are two
    val seq1 = gr1.forGroupRef(dummyGroupRef, 1).modelGroup.asInstanceOf[Sequence]

    //Explore LocalSimpleTypeDef
    val Seq(gr2c1, gr2c2, gr2c3) = gr2.forGroupRef(dummyGroupRef, 1).modelGroup.asInstanceOf[ModelGroup].groupMembers
    val ist = gr2c3.asInstanceOf[LocalElementDecl].immediateType.get.asInstanceOf[LocalSimpleTypeDef]
    assertEquals("tns:aType", ist.baseName)

    //Explore LocalElementDecl
    val led = gr2c1.asInstanceOf[LocalElementDecl]
    assertEquals(1, led.maxOccurs)
    val Seq(leda) = led.annotationObjs
    assertEquals("{ $myVar1 eq (+47 mod 4) }", leda.asInstanceOf[DFDLDiscriminator].testBody.get)

    // Explore sequence
    val Seq(seq1a: DFDLSequence) = seq1.annotationObjs // one format annotation with a property
    assertEquals(SeparatorPosition.Infix, seq1a.separatorPosition)
    val Seq(seq1e1, seq1s1) = seq1.groupMembers // has an element and a sub-sequence as its children.
    assertEquals(2, seq1e1.asInstanceOf[ElementRef].maxOccurs)
    assertEquals("ex:a", seq1e1.asInstanceOf[ElementRef].ref)
    assertEquals(1, seq1s1.asInstanceOf[Sequence].groupMembers.length) // it has the hidden group
  }

  @Test def test4 {
    val testSchema = XML.load(Misc.getRequiredResource("/test/example-of-most-dfdl-constructs.dfdl.xml"))
    val compiler = Compiler()

    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    val Seq(gd1, gd2, gd3, gd4, gd5) = sd.globalGroupDefs // Obtain Group nodes
    val ch1 = gd2.forGroupRef(dummyGroupRef, 1).modelGroup.asInstanceOf[Choice] // Downcast child-node of group to Choice
    val Seq(cd1, cd2, cd3) = ch1.groupMembers // Children nodes of Choice-node, there are 3

    val Seq(a1: DFDLChoice) = gd2.forGroupRef(dummyGroupRef, 1).modelGroup.annotationObjs // Obtain the annotation object that is a child
    // of the group node.

    assertEquals(AlignmentType.Implicit, a1.alignment)
    assertEquals(ChoiceLengthKind.Implicit, a1.choiceLengthKind)

    val Seq(asrt1) = cd2.asInstanceOf[LocalElementDecl].annotationObjs // Obtain Annotation object that is child
    // of cd2.

    assertEquals("{ $myVar1 eq xs:int(xs:string(fn:round-half-to-even(8.5))) }", asrt1.asInstanceOf[DFDLAssert].testTxt)
  }

  @Test def test_named_format_chaining {
    val testSchema =
      XML.load(
        Misc.getRequiredResource(
          "/test/example-of-named-format-chaining-and-element-simpleType-property-combining.dfdl.xml"))

    val compiler = Compiler()
    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    val Seq(ge1f, ge2f, ge3f, ge4f, ge5f, ge6f) = sd.globalElementDecls // Obtain global element nodes
    val ge1 = ge1f.forRoot()
    val Seq(a1: DFDLElement) = ge1.annotationObjs

    val props: Map[String, String] = a1.getFormatProperties()

    def foundValues(collection: Map[String, String], key: String, value: String): Boolean = {
      val found: Boolean = Option(collection.find(x => x._1 == key && x._2 == value)) match {
        case Some(_) => true
        case None => false
      }
      found
    }

    assertEquals(true, foundValues(props, "occursCountKind", "parsed"))
    assertEquals(true, foundValues(props, "lengthKind", "pattern"))
    assertEquals(true, foundValues(props, "representation", "text"))
    assertEquals(true, foundValues(props, "binaryNumberRep", "packed"))
  }

  @Test def test_simple_types_access_works {
    val testSchema =
      XML.load(
        Misc.getRequiredResource(
          "/test/example-of-named-format-chaining-and-element-simpleType-property-combining.dfdl.xml"))

    val compiler = Compiler()

    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    val Seq(ge1, ge2, ge3, ge4, ge5, ge6) = sd.globalElementDecls // Obtain global element nodes

    val x = ge2.forRoot().typeDef.asInstanceOf[LocalSimpleTypeDef]

    assertEquals(AlignmentUnits.Bytes, x.alignmentUnits)
  }

  @Test def test_simple_types_property_combining {
    val testSchema =
      XML.load(
        Misc.getRequiredResource(
          "/test/example-of-named-format-chaining-and-element-simpleType-property-combining.dfdl.xml"))

    val compiler = Compiler()

    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    val Seq(ge1f, ge2f, ge3f, ge4f, ge5f, ge6f) = sd.globalElementDecls // Obtain global element nodes

    val ge2 = ge2f.forRoot()
    val ge3 = ge3f.forRoot()
    val ge4 = ge4f.forRoot()
    val ge5 = ge5f.forRoot()
    val ge6 = ge6f.forRoot()

    assertEquals(AlignmentUnits.Bytes, ge2.alignmentUnits)

    assertEquals(AlignmentUnits.Bytes, ge3.alignmentUnits)
    assertEquals(NilKind.LiteralValue, ge3.nilKind)

    // Tests overlapping properties
    intercept[daffodil.dsom.SchemaDefinitionError] { ge4.lengthKind }

    assertEquals(AlignmentUnits.Bytes, ge5.alignmentUnits) // local
    assertEquals(OccursCountKind.Parsed, ge5.occursCountKind) // def1
    assertEquals(BinaryNumberRep.Bcd, ge5.binaryNumberRep) // def3
    assertEquals(NilKind.LiteralValue, ge5.nilKind) // local
    assertEquals(Representation.Text, ge5.representation) // def3
    assertEquals(LengthKind.Pattern, ge5.lengthKind) // superseded by local

    // Test Defaulting
    assertEquals(BinaryNumberRep.Packed, ge6.binaryNumberRep)
  }

  @Test def testTerminatingMarkup {
    val testSchema = TestUtils.dfdlTestSchema(
      <dfdl:format ref="tns:daffodilTest1"/>,
      <xs:element name="e1" dfdl:lengthKind="implicit">
        <xs:complexType>
          <xs:sequence dfdl:separator=",">
            <xs:element name="s1" type="xs:string" dfdl:lengthKind="explicit" dfdl:length="{ 1 }" minOccurs="0" dfdl:occursCountKind="parsed" dfdl:terminator=";"/>
            <xs:element name="s2" type="xs:string" dfdl:lengthKind="explicit" dfdl:length="{ 1 }"/>
          </xs:sequence>
        </xs:complexType>
      </xs:element>)
    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments
    val Seq(ge1f) = sd.globalElementDecls // Obtain global element nodes
    val ge1 = ge1f.forRoot()
    val ct = ge1.immediateType.get.asInstanceOf[LocalComplexTypeDef]
    val sq = ct.modelGroup.group.asInstanceOf[Sequence]
    val Seq(s1, s2) = sq.groupMembers.asInstanceOf[List[LocalElementDecl]]
    val s1tm = s1.terminatingMarkup
    val Seq(ce) = s1tm
    assertTrue(ce.isConstant)
    assertEquals(";", ce.constant)
  }

  @Test def testTerminatingMarkup2 {
    val testSchema = TestUtils.dfdlTestSchema(
      <dfdl:format ref="tns:daffodilTest1"/>,
      <xs:element name="e1" dfdl:lengthKind="implicit">
        <xs:complexType>
          <xs:sequence dfdl:separator="," dfdl:separatorPosition="infix" dfdl:separatorPolicy="required" dfdl:terminator=";">
            <xs:element name="s1" type="xs:string" dfdl:lengthKind="explicit" dfdl:length="{ 1 }" minOccurs="0" dfdl:occursCountKind="parsed"/>
            <xs:element name="s2" type="xs:string" dfdl:lengthKind="explicit" dfdl:length="{ 1 }"/>
          </xs:sequence>
        </xs:complexType>
      </xs:element>)
    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    val Seq(ge1f) = sd.globalElementDecls // Obtain global element nodes
    val ge1 = ge1f.forRoot()
    val ct = ge1.immediateType.get.asInstanceOf[LocalComplexTypeDef]
    val sq = ct.modelGroup.group.asInstanceOf[Sequence]
    val Seq(s1, s2) = sq.groupMembers.asInstanceOf[List[LocalElementDecl]]
    val s1tm = s1.terminatingMarkup
    val Seq(ce) = s1tm
    assertTrue(ce.isConstant)
    assertEquals(",", ce.constant)
    val s2tm = s2.terminatingMarkup
    val Seq(ce1, ce2) = s2tm
    assertTrue(ce1.isConstant)
    assertEquals(",", ce1.constant)
    assertTrue(ce2.isConstant)
    assertEquals(";", ce2.constant)
  }

  @Test def test_simpleType_base_combining {
    val testSchema = XML.load(Misc.getRequiredResource("/test/example-of-most-dfdl-constructs.dfdl.xml"))
    val compiler = Compiler()

    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    // No annotations
    val Seq(ct) = sd.globalComplexTypeDefs

    // Explore global element decl
    val Seq(e1f, e2f, e3f, e4f, e5f) = sd.globalElementDecls // there are 3 factories
    val e1 = e1f.forRoot()
    val e2 = e2f.forRoot()
    val e3 = e3f.forRoot()

    val Seq(gs1f, gs2f, gs3f, gs4f) = sd.globalSimpleTypeDefs

    val gs1 = gs1f.forElement(e1) // Global Simple Type - aType

    assertEquals("ex:aaType", gs1.restrictionBase)
    assertTrue(FindValue(gs1.allNonDefaultProperties, "alignmentUnits", "bytes")) // SimpleType - Local
    assertTrue(FindValue(gs1.allNonDefaultProperties, "byteOrder", "bigEndian")) // SimpleType - Base
    assertTrue(FindValue(gs1.allNonDefaultProperties, "occursCountKind", "implicit")) // Default Format
    assertTrue(FindValue(gs1.allNonDefaultProperties, "representation", "text")) // Define Format - def1
    assertTrue(FindValue(gs1.allNonDefaultProperties, "encoding", "utf-8")) // Define Format - def1
    assertTrue(FindValue(gs1.allNonDefaultProperties, "textStandardBase", "10")) // Define Format - def2
    assertTrue(FindValue(gs1.allNonDefaultProperties, "escapeSchemeRef", "tns:quotingScheme")) // Define Format - def2

    val gs3 = gs3f.forElement(e1) // Global SimpleType - aTypeError - overlapping base props

    // Tests overlapping properties
    intercept[daffodil.dsom.SchemaDefinitionError] { gs3.allNonDefaultProperties }
  }

  @Test def test_group_references {
    val testSchema = XML.load(Misc.getRequiredResource("/test/example-of-most-dfdl-constructs.dfdl.xml"))
    val compiler = Compiler()

    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    // No annotations
    val Seq(ct) = sd.globalComplexTypeDefs

    // Explore global element decl
    val Seq(e1f, e2f, e3f, e4f, e5f) = sd.globalElementDecls // there are 3 factories

    // GroupRefTest
    val e4 = e4f.forRoot() // groupRefTest

    val e4ct = e4.immediateType.get.asInstanceOf[LocalComplexTypeDef]

    val e4ctgref = e4ct.modelGroup.asInstanceOf[GroupRef] // groupRefTests' local group decl

    val myGlobal1 = e4ctgref.groupDef

    val myGlobal1Seq = myGlobal1.modelGroup.asInstanceOf[Sequence]

    val myGlobal2Seq = myGlobal1Seq.immediateGroup.get.asInstanceOf[Sequence]

    // val myGlobal2Seq = myGlobal2.modelGroup.asInstanceOf[Sequence]

    // myGlobal1 Properties
    assertTrue(FindValue(myGlobal1Seq.allNonDefaultProperties, "separator", ","))

    // myGlobal2 Properties
    assertTrue(FindValue(myGlobal2Seq.allNonDefaultProperties, "separator", ";"))
    assertTrue(FindValue(myGlobal2Seq.allNonDefaultProperties, "separatorPosition", "infix"))

    // GroupRefTestOverlap
    val exc = intercept[Exception] {

      val e5 = e5f.forRoot() // groupRefTestOverlap

      val e5ct = e5.immediateType.get.asInstanceOf[LocalComplexTypeDef]

      val e5ctgref = e5ct.modelGroup.asInstanceOf[GroupRef] // groupRefTestOverlap's local group decl

      val myGlobal3 = e5ctgref.groupDef
      val myGlobal3Seq = myGlobal3.modelGroup.asInstanceOf[Sequence]

      val overlaps = myGlobal3Seq.combinedGroupRefAndGlobalGroupDefProperties
      // Tests overlapping properties

    }
    val msg = exc.getMessage()
    assertTrue(msg.contains("Overlap"))

  }

  @Test def test_ibm_7132 {
    val ibm7132Schema = XML.load(Misc.getRequiredResource("/test/TestRefChainingIBM7132.dfdl.xml"))
    val compiler = Compiler()
    val sset = new SchemaSet(ibm7132Schema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    val Seq(ge1f) = sd.globalElementDecls // Obtain global element nodes
    val ge1 = ge1f.forRoot()

    val f1 = ge1.formatAnnotation

    val props: Map[String, String] = f1.getFormatProperties()

    assertEquals(true, FindValue(props, "separatorPosition", "infix"))
    assertEquals(true, FindValue(props, "lengthKind", "implicit"))
    assertEquals(true, FindValue(props, "representation", "text"))
    assertEquals(true, FindValue(props, "textNumberRep", "standard"))

    val ct = ge1.typeDef.asInstanceOf[ComplexTypeBase]
    val seq = ct.modelGroup.asInstanceOf[Sequence]

    val Seq(e1: ElementBase, e2: ElementBase) = seq.groupMembers

    val e1f = e1.formatAnnotation.asInstanceOf[DFDLElement]
    val e1fProps: Map[String, String] = e1f.getFormatProperties()

    //    println(e1fProps)
    //
    assertEquals(true, FindValue(e1fProps, "initiator", ""))
    //println(e1f.initiatorRaw)

    //e1f.initiatorRaw
    //e1f.byteOrderRaw
    e1f.lengthKind
  }

  @Test def testDfdlRef = {
    val testSchema = TestUtils.dfdlTestSchema(
      <dfdl:defineFormat name="ref1"> <dfdl:format initiator=":"/> </dfdl:defineFormat>,
      <xs:element name="e1" dfdl:lengthKind="implicit" dfdl:ref="tns:ref1" type="xs:string">
      </xs:element>)
    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    val Seq(ge1f) = sd.globalElementDecls // Obtain global element nodes
    val ge1 = ge1f.forRoot()
    val props = ge1.formatAnnotation.getFormatProperties()

    // println(props)
    //assertEquals(":", ge1.initiatorRaw)
  }

  @Test def testGetQName = {
    val testSchema = TestUtils.dfdlTestSchema(
      <dfdl:defineFormat name="ref1">
        <dfdl:format initiator=":"/>
      </dfdl:defineFormat>,
      <xs:element name="e1" dfdl:lengthKind="implicit" dfdl:ref="tns:ref1" type="xs:string">
      </xs:element>)
    // println(testSchema)
    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    val Seq(ge1f) = sd.globalElementDecls // Obtain global element nodes
    val ge1 = ge1f.forRoot()
    //val props = ge1.formatAnnotation.getFormatProperties()

    val (nsURI, localName) = ge1.formatAnnotation.getQName("ref1")

    // println(nsURI + ", " + localName)
    assertEquals("ref1", localName)
    assertEquals(XMLUtils.EXAMPLE_NAMESPACE, nsURI)
  }

  @Test def testGetAllNamespaces() {
    val xml = <bar xmlns:foo="fooNS" xmlns:bar="barNS">
                <quux xmlns:baz="bazNS" attr1="x"/>
              </bar>

    val scope = (xml \ "quux")(0).scope
    // println(scope)
    val newElem = scala.xml.Elem("dfdl", "element", null, scope)
    // println(newElem)
  }

  @Test def test_delim_inheritance {
    val delimiterInheritance = XML.load(Misc.getRequiredResource("/test/TestDelimiterInheritance.dfdl.xml"))

    val compiler = Compiler()
    val sset = new SchemaSet(delimiterInheritance)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    val Seq(ge1f) = sd.globalElementDecls // Obtain global element nodes
    val ge1 = ge1f.forRoot()

    val ct = ge1.typeDef.asInstanceOf[ComplexTypeBase]
    val seq = ct.modelGroup.asInstanceOf[Sequence]

    val Seq(e1: ElementBase, e2: ElementBase, e3: ElementBase) = seq.groupMembers

    assertEquals(3, e1.allTerminatingMarkup.length) // 1 Level + ref on global element decl
    assertEquals("a", e1.allTerminatingMarkup(0).prettyExpr)
    assertEquals("b", e1.allTerminatingMarkup(1).prettyExpr)
    assertEquals("g", e1.allTerminatingMarkup(2).prettyExpr)

    val ct2 = e3.asInstanceOf[ElementBase].typeDef.asInstanceOf[ComplexTypeBase]
    val seq2 = ct2.modelGroup.asInstanceOf[Sequence]

    val Seq(e3_1: ElementBase, e3_2: ElementBase) = seq2.groupMembers

    assertEquals(6, e3_1.allTerminatingMarkup.length) // 2 Level + ref on global element decl
    assertEquals("e", e3_1.allTerminatingMarkup(0).prettyExpr)
    assertEquals("c", e3_1.allTerminatingMarkup(1).prettyExpr)
    assertEquals("d", e3_1.allTerminatingMarkup(2).prettyExpr)
    assertEquals("a", e3_1.allTerminatingMarkup(3).prettyExpr)
    assertEquals("b", e3_1.allTerminatingMarkup(4).prettyExpr)
    assertEquals("g", e3_1.allTerminatingMarkup(5).prettyExpr)

    assertEquals(6, e3_2.allTerminatingMarkup.length) // 2 Level + ref on global element decl + ref on local element decl
    assertEquals("f", e3_2.allTerminatingMarkup(0).prettyExpr) // f instead of e, due to ref
    assertEquals("c", e3_2.allTerminatingMarkup(1).prettyExpr)
    assertEquals("d", e3_2.allTerminatingMarkup(2).prettyExpr)
    assertEquals("a", e3_2.allTerminatingMarkup(3).prettyExpr)
    assertEquals("b", e3_2.allTerminatingMarkup(4).prettyExpr)
    assertEquals("g", e3_2.allTerminatingMarkup(5).prettyExpr)
  }

  @Test def test_escapeSchemeOverride = {
    val testSchema = TestUtils.dfdlTestSchema(
      <dfdl:format separator="" initiator="" terminator="" emptyValueDelimiterPolicy="none" textNumberRep="standard" representation="text" occursStopValue="-1" occursCountKind="expression" escapeSchemeRef="pound"/>
      <dfdl:defineEscapeScheme name="pound">
        <dfdl:escapeScheme escapeCharacter='#' escapeKind="escapeCharacter"/>
      </dfdl:defineEscapeScheme>
      <dfdl:defineEscapeScheme name='cStyleComment'>
        <dfdl:escapeScheme escapeBlockStart='/*' escapeBlockEnd='*/' escapeKind="escapeBlock"/>
      </dfdl:defineEscapeScheme>,
      <xs:element name="list">
        <xs:complexType>
          <xs:sequence>
            <xs:element name="character" type="xsd:string" maxOccurs="unbounded" dfdl:representation="text" dfdl:separator="," dfdl:terminator="%NL;"/>
            <xs:element name="block" type="xsd:string" maxOccurs="unbounded" dfdl:representation="text" dfdl:separator="," dfdl:terminator="%NL;" dfdl:escapeSchemeRef="cStyleComment"/>
          </xs:sequence>
        </xs:complexType>
      </xs:element>)
    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    val Seq(ge1f) = sd.globalElementDecls // Obtain global element nodes
    val ge1 = ge1f.forRoot()

    val ct = ge1.typeDef.asInstanceOf[ComplexTypeBase]
    val seq = ct.modelGroup.asInstanceOf[Sequence]

    val Seq(e1: ElementBase, e2: ElementBase) = seq.groupMembers
    val e1f = e1.formatAnnotation.asInstanceOf[DFDLElement]
    val props = e1.allNonDefaultProperties ++ e1.defaultProperties

    val e1f_esref = e1.getProperty("escapeSchemeRef")
    // println(e1f_esref)

    assertEquals("pound", e1f_esref)

    // Should have escapeCharacter and escapeKind

    val e2f = e2.formatAnnotation.asInstanceOf[DFDLElement]
    val e2f_esref = e2.getProperty("escapeSchemeRef")
    // escapeBlockStart/End escapeBlockKind (NOTHING ELSE)
    assertEquals("cStyleComment", e2f_esref)
  }

  @Test def test_element_references {
    val testSchema = XML.load(Misc.getRequiredResource("/test/example-of-most-dfdl-constructs.dfdl.xml"))

    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    // No annotations
    val Seq(ct) = sd.globalComplexTypeDefs

    // g1.name == "gr"
    val Seq(g1: GlobalGroupDefFactory, g2, g3, g4, g5) = sd.globalGroupDefs

    val seq1 = g1.forGroupRef(dummyGroupRef, 1).modelGroup.asInstanceOf[Sequence]

    // e1.ref == "ex:a"
    val Seq(e1: ElementRef, s1: Sequence) = seq1.groupMembers

    assertEquals(2, e1.maxOccurs)
    assertEquals(1, e1.minOccurs)
    assertEquals(AlignmentUnits.Bytes, e1.alignmentUnits)
    //assertEquals(true, e1.nillable) // TODO: e1.nillable doesn't exist?
    //assertEquals("%ES; %% %#0; %NUL;%ACK; foo%#rF2;%#rF7;bar %WSP*; %#2024;%#xAABB; &amp;&#2023;&#xCCDD; -1", e1.nilValue) // TODO: Do not equal each other!
    assertEquals(NilKind.LiteralValue, e1.nilKind)
  }

  @Test def testPathWithIndexes() {
    val testSchema = TestUtils.dfdlTestSchema(
      <dfdl:format ref="tns:daffodilTest1"/>,
      <xs:element name="r" type="tns:myType"/>
      <xs:complexType name="myType">
        <xs:sequence>
          <xs:sequence/>
          <xs:sequence/>
          <xs:sequence>
            <xs:element name="s" type="xs:int"/>
          </xs:sequence>
        </xs:sequence>
      </xs:complexType>)
    val sset = new SchemaSet(testSchema)
    val Seq(sch) = sset.schemas
    val Seq(sd) = sch.schemaDocuments

    val Seq(ge1f) = sd.globalElementDecls // Obtain global element nodes
    val ge1 = ge1f.forRoot()

    val ct = ge1.typeDef.asInstanceOf[ComplexTypeBase]
    val seq = ct.modelGroup.asInstanceOf[Sequence]

    val Seq(s1, s2, s3) = seq.groupMembers
    val s3s = s3.asInstanceOf[Sequence]
    val Seq(es) = s3s.groupMembers
    val ese = es.asInstanceOf[LocalElementDecl]
    // println(ese)
    assertTrue(ese.path.contains("sequence[3]"))
  }

}

