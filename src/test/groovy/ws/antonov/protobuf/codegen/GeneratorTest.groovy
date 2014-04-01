package ws.antonov.protobuf.codegen

import com.google.protobuf.compiler.PluginProtos

/**
 * Created by aantonov on 3/27/14.
 */
class GeneratorTest extends GroovyTestCase {
    def InputStream sampleProtobuf = new FileInputStream("src/test/resources/sample_pkg_multifile_outer.pb")

    void setUp() {
        super.setUp()

    }

    void tearDown() {

    }

    void testGenerate() {
        def g = new Generator(System.getProperty("user.dir") + "/src/test/tpl")
        def out = new ByteArrayOutputStream()
        g.generate(sampleProtobuf, out)

        def response = PluginProtos.CodeGeneratorResponse.parseFrom(new ByteArrayInputStream(out.toByteArray()))

        assertNotNull(response)
        //response.getFile()
    }

    void testHandleField() {

    }

    void testHandleFile() {

    }

    void testHandleMessage() {

    }
}
