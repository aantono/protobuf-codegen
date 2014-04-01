package ws.antonov.protobuf.codegen

import com.google.protobuf.ByteString
import com.google.protobuf.DescriptorProtos
import com.google.protobuf.compiler.PluginProtos
import groovy.text.GStringTemplateEngine

/**
 * Created by aantonov on 3/27/14.
 */
class Generator {
    private GStringTemplateEngine engine = new GStringTemplateEngine()
    private File templateBaseDir
    private Map messageTypes = [:]

    private OutputStreamWriter debug = new FileWriter("debug.txt")

    Generator(String templateBaseDirName) {
        this.templateBaseDir = new File(templateBaseDirName.toString())
    }

    def generate(InputStream is, OutputStream io) {
        def request = PluginProtos.CodeGeneratorRequest.parseFrom(is)

//        OutputStream store = new FileOutputStream("sample_pkg_single_outer.pb")
//        request.writeTo(store)

        debug.println request

        def response = PluginProtos.CodeGeneratorResponse.newBuilder()

        populateMessageTypes(request)

        // Now we need to detect the desired code elements and process them appropriately
        request.protoFileList.each {
            // Loop through each FileDescriptorProto to pickup all the *.proto files that have been passed to the protoc compiler
            //debug.println it
            def file = it as DescriptorProtos.FileDescriptorProto

            handleFile(response, file)

            file.messageTypeList.each {
                // Loop through each DescriptorProto to pickup all the messages defined in each *.proto file.
                //debug.println it
                def msg = it as DescriptorProtos.DescriptorProto

                handleMessage(response, file, msg)

                processMessageStructure(response, file, msg)
            }
        }

        debug.flush()
        debug.close()
        response.build().writeTo(io)
    }

    private void populateMessageTypes(PluginProtos.CodeGeneratorRequest request) {
        request.protoFileList.each {
            def file = it as DescriptorProtos.FileDescriptorProto
            file.messageTypeList.each {
                def msg = it as DescriptorProtos.DescriptorProto
                recordMessageType(file, msg)
            }
        }
    }

    private void recordMessageType(DescriptorProtos.FileDescriptorProto file,
                                   DescriptorProtos.DescriptorProto msg,
                                   Collection<String> nestedMsgParentTypes = []) {
        messageTypes.put(".${generateInsertionScopeName(file, (nestedMsgParentTypes + msg.name).join("."))}".toString(),
                [file: file, msg: msg])
        msg.nestedTypeList.each {
            def nest = it as DescriptorProtos.DescriptorProto
            recordMessageType(file, nest, (nestedMsgParentTypes + msg.name))
        }
    }

    private void processMessageStructure(PluginProtos.CodeGeneratorResponse.Builder response,
                                         DescriptorProtos.FileDescriptorProto file,
                                         DescriptorProtos.DescriptorProto msg,
                                         Collection<String> nestedMsgParentTypes = []) {
        msg.fieldList.each {
            // Loop through each FieldDescriptorProto to pickup all the fields defined in each message.
            debug.println it
            def field = it as DescriptorProtos.FieldDescriptorProto

            handleField(response, file, msg, field, nestedMsgParentTypes)
        }

        msg.nestedTypeList.each {
            def nest = it as DescriptorProtos.DescriptorProto

            handleMessage(response, file, nest, (nestedMsgParentTypes + msg.name))

            processMessageStructure(response, file, nest, (nestedMsgParentTypes + msg.name))
        }
    }

    def handleField(PluginProtos.CodeGeneratorResponse.Builder response,
                    DescriptorProtos.FileDescriptorProto file,
                    DescriptorProtos.DescriptorProto msg,
                    DescriptorProtos.FieldDescriptorProto field,
                    Collection<String> nestedMsgParentTypes = []) {

        //Template name is either <JavaFile>_<proto_msg.name>_<proto_field.name>.tpl or
        //  <JavaFile>_<proto_msg.name>_default_field or default_field.tpl
        File templateFile = new File(templateBaseDir, generateFieldTemplatePath(file, msg, field))
        if (!templateFile.exists())
            templateFile = new File(templateBaseDir, generateFieldTemplatePath(file, msg))
            if (!templateFile.exists())
                templateFile = new File(templateBaseDir, "default_field.tpl")

        if(templateFile.exists()) {
            String msgBaseName = generateMessageFileName(file, msg, nestedMsgParentTypes)
            String msgFileName = msgBaseName + ".java"
            String pkgName = generateFileJavaPackageName(file)
            String pkgPathPrefix = pkgName.replace(".", "/")
            String filePath = pkgPathPrefix.isEmpty() ? msgFileName : pkgPathPrefix + "/" + msgFileName
            String javaFieldName = underscoreToCamelCase(field.name)
            String javaClassName = (nestedMsgParentTypes + msg.name).join(".")
            //String variableName = javaFieldName[0].toLowerCase() + javaFieldName.substring(1) + "AsMap_"

            def bindings = [
                javaFileName : msgFileName,
                javaFilePath : filePath,
                javaPackageName: pkgName,
                javaClassName : javaClassName,
                javaFieldName: javaFieldName,
                javaFieldType: generateFieldTypeName(messageTypes, field),
                templateFile : templateFile,
                protoFile    : file,
                protoMsg     : msg,
                protoField   : field,
                isFieldACollection : field.label == DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED
            ]

            def template = engine.createTemplate(templateFile).make(bindings)
            def text = template.toString()

            if (!text.isEmpty()) {
                def f = PluginProtos.CodeGeneratorResponse.File.newBuilder()
                f.setName(filePath).
                        setInsertionPoint("class_scope:" + generateInsertionScopeName(file, javaClassName)).
                        setContent(text)
                response.addFile(f)
            }
        }
    }

    def handleFile(PluginProtos.CodeGeneratorResponse.Builder response,
                   DescriptorProtos.FileDescriptorProto file) {
        //Template name is either <JavaFile>_<proto_msg.name>_builder.tpl or default_field.tpl
        File templateFile = new File(templateBaseDir, generateFileTemplatePath(file))
        if (!templateFile.exists())
            templateFile = new File(templateBaseDir, "default_outer.tpl")

        if(templateFile.exists()) {
            String fileBaseName = generateFileClassName(file)
            String javaFileName = fileBaseName + ".java"
            String pkgName = generateFileJavaPackageName(file)
            String pkgPathPrefix = pkgName.replace(".", "/")
            String filePath = pkgPathPrefix.isEmpty() ? javaFileName : pkgPathPrefix + "/" + javaFileName

            def bindings = [
                    javaFileName : javaFileName,
                    javaFilePath : filePath,
                    javaPackageName: pkgName,
                    javaClassName : fileBaseName,
                    templateFile : templateFile,
                    protoFile    : file
            ]

            def template = engine.createTemplate(templateFile).make(bindings)
            def text = template.toString()

            if (!text.isEmpty()) {
                def f = PluginProtos.CodeGeneratorResponse.File.newBuilder()
                f.setName(filePath).
                        setInsertionPoint("outer_class_scope").
                        setContent(text)
                response.addFile(f)
            }

        }

    }

    def handleMessage(PluginProtos.CodeGeneratorResponse.Builder response,
                      DescriptorProtos.FileDescriptorProto file,
                      DescriptorProtos.DescriptorProto msg,
                      Collection<String> nestedMsgParentTypes = []) {
        //Template name is either <JavaFile>_<proto_msg.name>_builder.tpl or default_field.tpl
        File templateFile = new File(templateBaseDir, generateMessageBuilderTemplatePath(file, msg))
        if (!templateFile.exists())
            templateFile = new File(templateBaseDir, "default_builder.tpl")

        String msgBaseName = generateMessageFileName(file, msg, nestedMsgParentTypes)
        String msgFileName = msgBaseName + ".java"
        String pkgName = generateFileJavaPackageName(file)
        String pkgPathPrefix = pkgName.replace(".", "/")
        String filePath = pkgPathPrefix.isEmpty() ? msgFileName : pkgPathPrefix + "/" + msgFileName
        String javaClassName = (nestedMsgParentTypes + msg.name).join(".")

        def bindings = [
            javaFileName : msgFileName,
            javaFilePath : filePath,
            javaPackageName: pkgName,
            javaClassName : javaClassName,
            templateFile : templateFile,
            protoFile    : file,
            protoMsg     : msg
        ]

        if(templateFile.exists()) {
            def template = engine.createTemplate(templateFile).make(bindings)
            def text = template.toString()

            if (!text.isEmpty()) {
                def f = PluginProtos.CodeGeneratorResponse.File.newBuilder()
                f.setName(filePath).
                        setInsertionPoint("builder_scope:" + generateInsertionScopeName(file, javaClassName)).
                        setContent(text)
                response.addFile(f)
            }

        }

        // Now we handle a class_scope template for generic (once-per-class content)
        templateFile = new File(templateBaseDir, generateMessageClassTemplatePath(file, msg))
        if (!templateFile.exists())
            templateFile = new File(templateBaseDir, "default_class.tpl")

        if(templateFile.exists()) {
            def template = engine.createTemplate(templateFile).make(bindings)
            def text = template.toString()

            if (!text.isEmpty()) {
                def f = PluginProtos.CodeGeneratorResponse.File.newBuilder()
                f.setName(filePath).
                        setInsertionPoint("class_scope:" + generateInsertionScopeName(file, javaClassName)).
                        setContent(text)
                response.addFile(f)
            }

        }

    }

    static String generateInsertionScopeName(DescriptorProtos.FileDescriptorProto file, String javaClassName) {
        if (!file.package.isEmpty())
            return [file.package, javaClassName].join(".")
        else
            return javaClassName
    }

    static String generateFileJavaPackageName(DescriptorProtos.FileDescriptorProto file) {
        String result

        if (file.options.hasJavaPackage())
            result = file.options.javaPackage
        else {
            result = ""
            if (!file.package.isEmpty()) {
                if (!result?.isEmpty())
                    result += '.'

                result += file.package
            }
        }

        return result;
    }

    static String generateFieldTypeName(Map messageTypes,
                                        DescriptorProtos.FieldDescriptorProto field) {
        switch (field.type) {
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE:
                def type = messageTypes[field.typeName]
                DescriptorProtos.FileDescriptorProto definingFile = type.file
                DescriptorProtos.DescriptorProto definingMsg = type.msg

                String javaPkgName = generateFileJavaPackageName(definingFile)
                String className
                if (!definingFile.package.isEmpty())
                    // If defining proto file has a proto package declared, remove it
                    className = field.typeName.substring(1).replace(definingFile.package, "").substring(1) // Remove the leading '.' character
                else
                    // If no proto package is defined
                    className = field.typeName.substring(1) // Remove the leading '.' character

                if (!definingFile.options.hasJavaMultipleFiles())
                    // If defining proto file does not declare separate java files for classes, add "wrapper" class
                    className = generateFileClassName(definingFile) + "." + className

                if (!javaPkgName.isEmpty())
                    // If java package option is defined, join that with the type name
                    return javaPkgName + "." + className
                else
                    // No package is defined in neither proto or java option, so return package-less type name
                    return className
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING:
                return String.class.getSimpleName()
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE:
                return "double"
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL:
                return "boolean"
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32:
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32:
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32:
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32:
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED32:
                return "int"
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT64:
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT64:
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64:
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED64:
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64:
                return "long"
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT:
                return "float"
            case DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES:
                return ByteString.class.name
            default:
                return Object.class.getSimpleName()
        }
    }

    static String generateFileTemplatePath(DescriptorProtos.FileDescriptorProto file) {
        String pkgPrefix = generateFileJavaPackageName(file).replace(".", "/")
        if (file.options.hasJavaMultipleFiles()) {
            // Each class will be in its own file
            return [pkgPrefix + "_outer.tpl"].join("/")
        } else {
            String outerClassName = generateFileClassName(file)
            return [pkgPrefix, outerClassName + "_outer.tpl"].join("/")
        }
    }

    static String generateMessageClassTemplatePath(DescriptorProtos.FileDescriptorProto file,
                                              DescriptorProtos.DescriptorProto msg) {
        String pkgPrefix = generateFileJavaPackageName(file).replace(".", "/")
        String msgNameSegment = msg.name + "_class"
        if (file.options.hasJavaMultipleFiles()) {
            // Each class will be in its own file
            return [pkgPrefix, msgNameSegment + ".tpl"].join("/")
        } else {
            String outerClassName = generateFileClassName(file)
            return [pkgPrefix, outerClassName + "_" + msgNameSegment + ".tpl"].join("/")
        }
    }

    static String generateMessageBuilderTemplatePath(DescriptorProtos.FileDescriptorProto file,
                                            DescriptorProtos.DescriptorProto msg) {
        String pkgPrefix = generateFileJavaPackageName(file).replace(".", "/")
        String msgNameSegment = msg.name + "_builder"
        if (file.options.hasJavaMultipleFiles()) {
            // Each class will be in its own file
            return [pkgPrefix, msgNameSegment + ".tpl"].join("/")
        } else {
            String outerClassName = generateFileClassName(file)
            return [pkgPrefix, outerClassName + "_" + msgNameSegment + ".tpl"].join("/")
        }
    }

    static String generateFieldTemplatePath(DescriptorProtos.FileDescriptorProto file,
                                       DescriptorProtos.DescriptorProto msg,
                                       DescriptorProtos.FieldDescriptorProto field = null) {
        String pkgPrefix = generateFileJavaPackageName(file).replace(".", "/")
        String msgFieldNameSegment = msg.name + "_" +(field ? field.name : "default_field")
        if (file.options.hasJavaMultipleFiles()) {
            // Each class will be in its own file
            return [pkgPrefix, msgFieldNameSegment + ".tpl"].join("/")
        } else {
            String outerClassName = generateFileClassName(file)
            return [pkgPrefix, outerClassName + "_" + msgFieldNameSegment + ".tpl"].join("/")
        }
    }

    // TODO: Come up with a better name for this method
    //  What it really returns is the Java file base (without .java ext) for where this message is declared
    static String generateMessageFileName(DescriptorProtos.FileDescriptorProto file,
                                          DescriptorProtos.DescriptorProto msg,
                                          Collection<String> nestedMsgParentTypes = []) {
        // First check if there is a separate outer class
        if (!file.options.hasJavaMultipleFiles()) {
            return generateFileClassName(file)
        } else if (nestedMsgParentTypes.size() > 0) {
            return nestedMsgParentTypes[0]
        } else {
            return msg.name
        }
    }

    static String generateFileClassName(DescriptorProtos.FileDescriptorProto file) {
        if (file.options.hasJavaOuterClassname())
            return file.options.javaOuterClassname
        else {
            return generateClassName(file.name)
        }
    }

    static String generateClassName(String name) {
        String basename
        int last_slash = name.lastIndexOf('/');

        if (last_slash == null)
            basename = name
        else
            basename = name.substring(last_slash + 1);

        return underscoreToPascalCase(basename.replace(".proto", ""))
    }

    static String underscoreToCamelCase(String underscore){
        if(!underscore || underscore.isAllWhitespace()) {
            return ''
        }
        if (underscore.indexOf("_") == -1) {
            return underscore[0].toLowerCase() + underscore.substring(1)
        }
        return underscore.replaceAll(/_\w/){ it[1].toUpperCase() }
    }

    static String underscoreToPascalCase(String underscore){
        if(!underscore || underscore.isAllWhitespace()) {
            return ''
        }
        if (underscore.indexOf("_") == -1) {
            return underscore[0].toUpperCase() + underscore.substring(1)
        }
        return underscore.replaceAll(/_\w/){ it[1].toUpperCase() }
    }
}
