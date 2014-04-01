<%
if (protoField.hasOptions() && protoField.options.hasExperimentalMapKey()) {
    String variableName = javaFieldName + "AsMap_"
    String getterName = javaFieldName[0].toUpperCase() + javaFieldName.substring(1)
    String mapKey = protoField.options.getExperimentalMapKey()
    mapKey = mapKey[0].toUpperCase() + mapKey.substring(1)
%>
// Some comments for ${javaClassName} (${javaFieldType}) ${variableName} in ${javaFileName}
// Content loaded from ${templateFile.absolutePath}
// This is the Map getter for the List
private java.util.Map<Object, ${javaFieldType}> ${variableName};
public java.util.Map<Object, ${javaFieldType}> get${getterName}Map() {
    if (${variableName} == null) {
        ${variableName} = new java.util.HashMap<Object, ${javaFieldType}>();
        for (${javaFieldType} o: get${getterName}List()) {
            ${variableName}.put(o.get${mapKey}(), o);
        }
    }
    return java.util.Collections${variableName};
}
<% } %>