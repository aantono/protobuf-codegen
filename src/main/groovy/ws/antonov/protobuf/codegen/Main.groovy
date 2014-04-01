import ws.antonov.protobuf.codegen.Generator

class Main {
    public static void main(String[] args) {
        //println "Welcome to Protobuf CodeGen Plugin"
        String templateBaseDir = System.getProperty("protobuf.codegen.template.dir")
        if (!templateBaseDir)
            templateBaseDir = System.getProperty("user.dir")

        new Generator(templateBaseDir).generate(System.in, System.out)

        System.exit(0)
    }
}