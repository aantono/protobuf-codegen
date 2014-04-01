<%
String getterName = javaFieldName[0].toUpperCase() + javaFieldName.substring(1)
if (isFieldACollection) {
  getterName += "List"
}
%>
// Some comments for ${javaClassName} (${javaFieldType}) ${javaFieldName} in ${javaFileName}
// Content loaded from ${templateFile.absolutePath}
public ${isFieldACollection ? 'java.util.List<' : ''}${javaFieldType}${isFieldACollection ? '>' : ''} test${getterName}() {
   return get${getterName}();
}

<% if (javaFieldType == 'int') { %>
public Integer get${getterName}AsInteger() {
   return Integer.valueOf(get${getterName}());
}
<% } %>
