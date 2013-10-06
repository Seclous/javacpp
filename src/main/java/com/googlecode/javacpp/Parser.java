/*
 * Copyright (C) 2013 Samuel Audet
 *
 * This file is part of JavaCPP.
 *
 * JavaCPP is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version (subject to the "Classpath" exception
 * as provided in the LICENSE.txt file that accompanied this code).
 *
 * JavaCPP is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with JavaCPP.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.googlecode.javacpp;

import java.io.*;
import java.util.*;

/**
 * To do:
 * - Name overloaded operators
 * - Enhance support for templates
 * - Handle anonymous struct and union
 * - etc.
 *
 * @author Samuel Audet
 */
public class Parser {

    public static class Exception extends java.lang.Exception {
        public Exception(String message) { super(message); }
        public Exception(String message, Throwable cause) { super(message, cause); }
    }

    public static class Info implements Cloneable {
        public Info() { }
        public Info(String ... cppNames) { this.cppNames = cppNames; }

        String[] cppNames = null, javaNames = null, annotations = null,
                 valueTypes = null, pointerTypes = null, genericTypes = null;
        boolean cast = false, define = false, complex = false, opaque = false, parse = false;
        String textBefore = null, textAfter = null;

        public Info cppNames(String ... cppNames) { this.cppNames = cppNames; return this; }
        public Info javaNames(String ... javaNames) { this.javaNames = javaNames; return this; }
        public Info annotations(String ... annotations) { this.annotations = annotations; return this; }
        public Info valueTypes(String ... valueTypes) { this.valueTypes = valueTypes; return this; }
        public Info pointerTypes(String ... pointerTypes) { this.pointerTypes = pointerTypes; return this; }
        public Info genericTypes(String ... genericTypes) { this.genericTypes = genericTypes; return this; }
        public Info cast(boolean cast) { this.cast = cast; return this;  }
        public Info define(boolean define) { this.define = define; return this; }
        public Info complex(boolean complex) { this.complex = complex; return this; }
        public Info opaque(boolean opaque) { this.opaque = opaque; return this; }
        public Info parse(boolean parse) { this.parse = parse; return this; }
        public Info textBefore(String textBefore) { this.textBefore = textBefore; return this; }
        public Info textAfter(String textAfter) { this.textAfter = textAfter; return this; }

        @Override public Info clone() {
            Info i = new Info();
            i.cppNames = cppNames != null ? cppNames.clone() : null;
            i.javaNames = javaNames != null ? javaNames.clone() : null;
            i.annotations = annotations != null ? annotations.clone() : null;
            i.valueTypes = valueTypes != null ? valueTypes.clone() : null;
            i.pointerTypes = pointerTypes != null ? pointerTypes.clone() : null;
            i.genericTypes = genericTypes != null ? genericTypes.clone() : null;
            i.cast = cast;
            i.define = define;
            i.opaque = opaque;
            i.textBefore = textBefore;
            i.textAfter = textAfter;
            return i;
        }
    }

    public static class InfoMap extends HashMap<String,LinkedList<Info>> {
        public InfoMap() { this.parent = defaults; }
        public InfoMap(InfoMap parent) { this.parent = parent; }

        InfoMap parent = null;
        static final InfoMap defaults = new InfoMap(null)
            .put(new Info("void").valueTypes("void").pointerTypes("Pointer"))
            .put(new Info("FILE").pointerTypes("Pointer").cast(true))
            .put(new Info("va_list").pointerTypes("Pointer").cast(true))

            .put(new Info("int8_t", "jbyte", "signed char")
                .valueTypes("byte").pointerTypes("BytePointer", "ByteBuffer", "byte[]"))
            .put(new Info("uint8_t", "char", "unsigned char")
                .valueTypes("byte").pointerTypes("BytePointer", "ByteBuffer", "byte[]").cast(true))

            .put(new Info("int16_t", "jshort", "short", "signed short", "short int", "signed short int")
                .valueTypes("short").pointerTypes("ShortPointer", "ShortBuffer", "short[]"))
            .put(new Info("uint16_t", "unsigned short", "unsigned short int")
                .valueTypes("short").pointerTypes("ShortPointer", "ShortBuffer", "short[]").cast(true))

            .put(new Info("int32_t", "jint", "int", "signed int", "signed")
                .valueTypes("int").pointerTypes("IntPointer", "IntBuffer", "int[]"))
            .put(new Info("uint32_t", "unsigned int", "unsigned")
                .valueTypes("int").pointerTypes("IntPointer", "IntBuffer", "int[]").cast(true))

            .put(new Info("int64_t", "__int64", "jlong", "long long", "signed long long", "long long int", "signed long long int")
                .valueTypes("long").pointerTypes("LongPointer", "LongBuffer", "long[]"))
            .put(new Info("uint64_t", "__uint64", "unsigned long long", "unsigned long long int")
                .valueTypes("long").pointerTypes("LongPointer", "LongBuffer", "long[]").cast(true))

            .put(new Info("long", "signed long", "long int", "signed long int")
                .valueTypes("long").pointerTypes("CLongPointer"))
            .put(new Info("unsigned long", "unsigned long int")
                .valueTypes("long").pointerTypes("CLongPointer").cast(true))

            .put(new Info("size_t").valueTypes("long").pointerTypes("SizeTPointer"))
            .put(new Info("float", "jfloat").valueTypes("float").pointerTypes("FloatPointer", "FloatBuffer", "float[]"))
            .put(new Info("double", "jdouble").valueTypes("double").pointerTypes("DoublePointer", "DoubleBuffer", "double[]"))
            .put(new Info("bool", "jboolean").valueTypes("boolean").pointerTypes("BoolPointer").cast(true))
            .put(new Info("const char").valueTypes("byte").pointerTypes("@Cast(\"const char*\") BytePointer", "String"))
            .put(new Info("std::string").valueTypes("@StdString BytePointer", "@StdString String"))
            .put(new Info("wchar_t", "WCHAR").valueTypes("char").pointerTypes("CharPointer").cast(true))

            .put(new Info("position").javaNames("_position"))
            .put(new Info("limit").javaNames("_limit"))
            .put(new Info("capacity").javaNames("_capacity"));

        static String sort(String name) {
            return sort(name, false);
        }
        static String sort(String name, boolean unconst) {
            if (name == null) {
                return null;
            }
            TreeSet<String> set = new TreeSet<String>();
            try {
                Tokenizer tokenizer = new Tokenizer(new StringReader(name));
                Token token;
                while (!(token = tokenizer.nextToken()).isEmpty()) {
                    set.add(token.value);
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
            boolean foundConst = false;
            name = "";
            for (String s : set) {
                if ("const".equals(s)) {
                    foundConst = true;
                } else {
                    name += s + " ";
                }
            }
            if (!unconst && foundConst) {
                return "const " + name.trim();
            } else {
                return name.trim();
            }
        }

        public LinkedList<Info> get(String cppName) {
            String key = sort(cppName, false);
            LinkedList<Info> infoList = super.get(key);
            if (infoList == null) {
                key = sort(cppName, true);
                infoList = super.get(key);
            }
            if (infoList == null && parent != null) {
                infoList = parent.get(cppName);
            }
            if (infoList == null) {
                infoList = new LinkedList<Info>();
            }
            return infoList;
        }

        public InfoMap put(Info info) {
            for (String cppName : info.cppNames != null ? info.cppNames : new String[] { null }) {
                String key = sort(cppName, false);
                LinkedList<Info> infoList = super.get(key);
                if (infoList == null) {
                    infoList = new LinkedList<Info>();
                }
                if (!infoList.contains(info)) {
                    infoList.add(info);
                }
                super.put(key, infoList);
            }
            return this;
        }
    }

    public static interface InfoMapper {
        void map(InfoMap infoMap);
    }

    static class Token implements Cloneable {
        Token() { }
        Token(int type, String value) { this.type = type; this.value = value; }

        static final int
                INTEGER    = 1,
                FLOAT      = 2,
                STRING     = 3,
                COMMENT    = 4,
                IDENTIFIER = 5,
                SYMBOL     = 6;

        static final Token
                EOF       = new Token(),
                CONST     = new Token(IDENTIFIER, "const"),
                DEFINE    = new Token(IDENTIFIER, "define"),
                IF        = new Token(IDENTIFIER, "if"),
                IFDEF     = new Token(IDENTIFIER, "ifdef"),
                IFNDEF    = new Token(IDENTIFIER, "ifndef"),
                ELIF      = new Token(IDENTIFIER, "elif"),
                ELSE      = new Token(IDENTIFIER, "else"),
                ENDIF     = new Token(IDENTIFIER, "endif"),
                ENUM      = new Token(IDENTIFIER, "enum"),
                EXTERN    = new Token(IDENTIFIER, "extern"),
                INLINE    = new Token(IDENTIFIER, "inline"),
                STATIC    = new Token(IDENTIFIER, "static"),
                CLASS     = new Token(IDENTIFIER, "class"),
                STRUCT    = new Token(IDENTIFIER, "struct"),
                UNION     = new Token(IDENTIFIER, "union"),
                TEMPLATE  = new Token(IDENTIFIER, "template"),
                TYPEDEF   = new Token(IDENTIFIER, "typedef"),
                TYPENAME  = new Token(IDENTIFIER, "typename"),
                NAMESPACE = new Token(IDENTIFIER, "namespace"),
                OPERATOR  = new Token(IDENTIFIER, "operator"),
                PRIVATE   = new Token(IDENTIFIER, "private"),
                PROTECTED = new Token(IDENTIFIER, "protected"),
                PUBLIC    = new Token(IDENTIFIER, "public"),
                VIRTUAL   = new Token(IDENTIFIER, "virtual");

        File file = null;
        int lineNumber = 0, type = -1;
        String spacing = "", value = "";

        boolean match(Object ... tokens) {
            boolean found = false;
            for (Object t : tokens) {
                found = found || equals(t);
            }
            return found;
        }

        Token expect(Object ... tokens) throws Exception {
            if (!match(tokens)) {
                throw new Exception(file + ":" + lineNumber + ": Unexpected token '" + toString() + "'");
            }
            return this;
        }

        boolean isEmpty() {
            return type == -1 && spacing.isEmpty();
        }

        @Override public Token clone() {
            Token t = new Token();
            t.file = file;
            t.lineNumber = lineNumber;
            t.type = type;
            t.spacing = spacing;
            t.value = value;
            return t;
        }

        @Override public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            } else if (obj == null) {
                return false;
            } else if (obj.getClass() == Integer.class) {
                return type == (Integer)obj;
            } else if (obj.getClass() == Character.class) {
                return type == (Character)obj;
            } else if (obj.getClass() == String.class) {
                return obj.equals(value);
            } else if (obj.getClass() == getClass()) {
                Token other = (Token)obj;
                return type == other.type && ((value == null && other.value == null) ||
                                               value != null && value.equals(other.value));
            } else {
                return false;
            }
        }

        @Override public int hashCode() {
            return type;
        }

        @Override public String toString() {
            return value != null && value.length() > 0 ? value : String.valueOf((char)type);
        }
    }

    static class Tokenizer implements Closeable {
        Tokenizer(Reader reader) {
            this.reader = reader;
        }
        Tokenizer(File file) throws FileNotFoundException {
            this.file = file;
            this.reader = new BufferedReader(new FileReader(file));
        }

        File file = null;
        Reader reader = null;
        String lineSeparator = null;
        int lastChar = -1, lineNumber = 1;
        StringBuilder buffer = new StringBuilder();

        public void close() throws IOException {
            reader.close();
        }

        int readChar() throws IOException {
            if (lastChar != -1) {
                int c = lastChar;
                lastChar = -1;
                return c;
            }
            int c = reader.read();
            if (c == '\r' || c == '\n') {
                lineNumber++;
                int c2 = c == '\r' ? reader.read() : -1;
                if (lineSeparator == null) {
                    lineSeparator = c == '\r' && c2 == '\n' ? "\r\n" :
                                    c == '\r' ? "\r" : "\n";
                }
                if (c2 != '\n') {
                    lastChar = c2;
                }
                c = '\n';
            }
            return c;
        }

        public Token nextToken() throws IOException {
            Token token = new Token();
            int c = readChar();

            buffer.setLength(0);
            if (Character.isWhitespace(c)) {
                buffer.append((char)c);
                while ((c = readChar()) != -1 && Character.isWhitespace(c)) {
                    buffer.append((char)c);
                }
            }
            token.file = file;
            token.lineNumber = lineNumber;
            token.spacing = buffer.toString();

            buffer.setLength(0);
            if (Character.isLetter(c) || c == '_') {
                token.type = Token.IDENTIFIER;
                buffer.append((char)c);
                while ((c = readChar()) != -1 && (Character.isDigit(c) || Character.isLetter(c) || c == '_')) {
                    buffer.append((char)c);
                }
                token.value = buffer.toString();
                lastChar = c;
            } else if (Character.isDigit(c) || c == '.' || c == '-' ||  c == '+') {
                token.type = c == '.' ? Token.FLOAT : Token.INTEGER;
                buffer.append((char)c);
                int prevc = 0;
                while ((c = readChar()) != -1 && (Character.isDigit(c) || c == '.' || c == '-' || c == '+' ||
                       (c >= 'a' && c <= 'f') || c == 'l' || c == 'u' || c == 'x' ||
                       (c >= 'A' && c <= 'F') || c == 'L' || c == 'U' || c == 'X')) {
                    if (c == '.') {
                        token.type = Token.FLOAT;
                    }
                    if (c != 'u' && c != 'U') {
                        buffer.append((char)c);
                    }
                    prevc = c;
                }
                if (prevc == 'f' || prevc == 'F') {
                    token.type = Token.FLOAT;
                }
                token.value = buffer.toString();
                if (token.type == Token.INTEGER && token.value.endsWith("LL")) {
                    token.value = token.value.substring(0, token.value.length() - 1);
                }
                lastChar = c;
            } else if (c == '"') {
                token.type = Token.STRING;
                buffer.append('"');
                int prevc = 0;
                while ((c = readChar()) != -1 && (prevc == '\\' || c != '"')) {
                    buffer.append((char)c);
                    prevc = c;
                }
                buffer.append('"');
                token.value = buffer.toString();
            } else if (c == '/') {
                c = readChar();
                if (c == '/') {
                    token.type = Token.COMMENT;
                    buffer.append('/').append('/');
                    int prevc = 0;
                    while ((c = readChar()) != -1 && (prevc == '\\' || c != '\n')) {
                        buffer.append((char)c);
                        prevc = c;
                    }
                    token.value = buffer.toString();
                    lastChar = c;
                } else if (c == '*') {
                    token.type = Token.COMMENT;
                    buffer.append('/').append('*');
                    int prevc = 0;
                    while ((c = readChar()) != -1 && (prevc != '*' || c != '/')) {
                        buffer.append((char)c);
                        prevc = c;
                    }
                    buffer.append('/');
                    token.value = buffer.toString();
                } else {
                    lastChar = c;
                    token.type = '/';
                }
            } else if (c == ':') {
                int c2 = readChar();
                if (c2 == ':') {
                    token.type = Token.SYMBOL;
                    token.value = "::";
                } else {
                    token.type = c;
                    lastChar = c2;
                }
            } else {
                if (c == '\\') {
                    int c2 = readChar();
                    if (c2 == '\n') {
                        String s = token.spacing;
                        token = nextToken();
                        token.spacing = s;
                        return token;
                    } else {
                        lastChar = c2;
                    }
                }
                token.type = c;
            }
            return token;
        }
    }


    public Parser(Properties properties, InfoMap infoMap) {
        this.properties = properties;
        this.infoMap = infoMap;
    }

    Properties properties = null;
    InfoMap infoMap = null;
    Token[] tokenArray = null;
    int tokenIndex = 0;

    int skipComments(int i, int j) {
        while (i < tokenArray.length) {
            if (!tokenArray[i].match(Token.COMMENT) && --j < 0) {
                break;
            }
            i++;
        }
        return i;
    }

    Token getToken() {
        return getToken(0);
    }
    Token getToken(int i) {
        return getToken(i, true);
    }
    Token getToken(int i, boolean skipComment) {
        int k = skipComment ? skipComments(tokenIndex, i) : tokenIndex + i;
        return k < tokenArray.length ? tokenArray[k] : Token.EOF;
    }
    Token nextToken() {
        return nextToken(true);
    }
    Token nextToken(boolean skipComment) {
        tokenIndex = skipComment ? skipComments(tokenIndex, 1) : tokenIndex + 1;
        return tokenIndex < tokenArray.length ? tokenArray[tokenIndex] : Token.EOF;
    }


    static String rescan(String lines, String spacing) {
        String text = "";
        Scanner scanner = new Scanner(lines);
        while (scanner.hasNextLine()) {
            text += spacing + scanner.nextLine();
            int newline = spacing.lastIndexOf('\n');
            if (newline >= 0) {
                spacing = spacing.substring(newline);
            }
        }
        return text;
    }


    String vectors() {
        String definitions = "";
        LinkedList<Info> infoList = infoMap.get("std::vector");
        for (Info info : infoList) {
            if (info.genericTypes == null || info.genericTypes.length == 0 ||
                    info.pointerTypes == null || info.pointerTypes.length == 0) {
                continue;
            }
            String cppType = info.genericTypes[0];
            String cppVectorType = "std::vector<" + cppType + ">";
            String javaVectorType = info.pointerTypes[0];
            String annotations = "@ByRef ";
            String javaType = cppType;
            LinkedList<Info> infoList2 = infoMap.get(cppType);
            if (infoList2.size() > 0) {
                Info info2 = infoList2.getFirst();
                if (info2.pointerTypes != null && info2.pointerTypes.length > 0) {
                    javaType = info2.pointerTypes[0];
                } else if (info2.valueTypes != null && info2.valueTypes.length > 0) {
                    javaType = info2.valueTypes[0];
                }
                int n = javaType.lastIndexOf(' ');
                if (n >= 0) {
                    annotations = javaType.substring(0, n + 1);
                    javaType = javaType.substring(n + 1);
                }
            }
            infoMap.put(new Info(cppVectorType).pointerTypes(javaVectorType));
            definitions = "\n" +
                    "@Name(\"" + cppVectorType + "\") public static class " + javaVectorType + " extends Pointer {\n" +
                    "    static { Loader.load(); }\n" +
                    "    public " + javaVectorType + "(Pointer p) { super(p); }\n" +
                    "    public " + javaVectorType + "(" + javaType + " ... array) { this(array.length); put(array); }\n" +
                    "    public " + javaVectorType + "()       { allocate();  }\n" +
                    "    public " + javaVectorType + "(long n) { allocate(n); }\n" +
                    "    private native void allocate();\n" +
                    "    private native void allocate(@Cast(\"size_t\") long n);\n\n" +

                    "    public native long size();\n" +
                    "    public native void resize(@Cast(\"size_t\") long n);\n\n" +

                    "    @Index public native " + annotations + javaType + " get(@Cast(\"size_t\") long i);\n" +
                    "    public native " + javaVectorType + " put(@Cast(\"size_t\") long i, " + javaType + " value);\n\n" +

                    "    public " + javaVectorType + " put(" + javaType + " ... array) {\n" +
                    "        if (size() < array.length) { resize(array.length); }\n" +
                    "        for (int i = 0; i < array.length; i++) {\n" +
                    "            put(i, array[i]);\n" +
                    "        }\n" +
                    "        return this;\n" +
                    "    }\n" +
                    "}\n";
        }
        return definitions;
    }


    static class TemplateMap extends LinkedHashMap<String,String> {
        TemplateMap(TemplateMap defaults) {
            this.defaults = defaults;
        }
        LinkedHashMap<String,String> defaults = null;

        String get(String key) {
            String value = super.get(key);
            if (value == null && defaults != null) {
                return defaults.get(key);
            } else {
                return value;
            }
        }
    }

    TemplateMap template(TemplateMap defaults) throws Exception {
        if (!getToken().match(Token.TEMPLATE)) {
            return defaults;
        }
        TemplateMap map = new TemplateMap(defaults);

        nextToken().expect('<');
        for (Token token = nextToken(); !token.match(Token.EOF); token = nextToken()) {
            if (token.match(Token.CLASS, Token.TYPENAME)) {
                map.put(nextToken().expect(Token.IDENTIFIER).value, null);
            }
            if (nextToken().expect(',', '>').match('>')) {
                nextToken();
                break;
            }
        }
        return map;
    }


    static class Declarator {
        int infoNumber = 0, indices = 0;
        boolean constValue = false, constPointer = false;
        String annotations = "", cppType = "", javaType = "", objectName = "", convention = "", definitions = "", parameters = "";
    }

    Declarator declarator(String namespace, TemplateMap typeMap, String defaultName,
            int infoNumber, int varNumber, boolean arrayAsPointer, boolean pointerAsArray) throws Exception {
        boolean isTypedef = getToken().match(Token.TYPEDEF);
        Declarator decl = new Declarator();
        int count = 0;
        String cppName = "";
        boolean simpleType = false;
        for (Token token = getToken(); !token.match(Token.EOF); token = nextToken()) {
            if (token.match("::")) {
                cppName += token;
            } else if (token.match('<')) {
                cppName += token;
                count++;
            } else if (token.match('>')) {
                cppName += token;
                count--;
            } else if (!token.match(Token.IDENTIFIER)) {
                break;
            } else if (token.match(Token.CONST)) {
                decl.constValue = true;
                continue;
            } else if (token.match(Token.TYPEDEF, Token.ENUM, Token.CLASS, Token.STRUCT, Token.UNION)) {
                continue;
            } else if (token.match("signed", "unsigned", "char", "short", "int", "long", "bool", "float", "double")) {
                if (!simpleType && count == 0) {
                    cppName = token.value + " ";
                } else {
                    cppName += token.value + " ";
                }
                simpleType = true;
            } else if (cppName.length() > 0 && !cppName.endsWith("::") && count == 0 &&
                    !getToken(1).match('*', '&', Token.IDENTIFIER, Token.CONST)) {
                // we probably reached a variable or function name identifier
                break;
            } else {
                LinkedList<Info> infoList = infoMap.get(token.value);
                if (infoList.size() > 0 && infoList.getFirst().annotations != null) {
                    for (String s : infoList.getFirst().annotations) {
                        decl.annotations += s + " ";
                    }
                } else {
                    if (cppName.endsWith("::") || count > 0) {
                        cppName += token.value;
                    } else {
                        cppName = token.value;
                    }
                }
            }
        }
        cppName = cppName.trim();
        if (typeMap != null && typeMap.containsKey(cppName)) {
            cppName = typeMap.get(cppName);
        }
        decl.cppType = decl.javaType = cppName;
        if ("...".equals(getToken().value)) {
            nextToken();
            return null;
        }

        count = 0;
        for (Token token = getToken(); varNumber > 0 && !token.match(Token.EOF); token = nextToken()) {
            if (token.match('{')) {
                body();
                token = getToken();
            }

            if (token.match('(')) {
                count++;
            } else if (token.match(')')) {
                count--;
            } else if (count != 0) {
                continue;
            } else if (token.match(',')) {
                varNumber--;
            } else if (token.match(';')) {
                nextToken();
                return null;
            }
        }

        String cast = cppName;
        int indirections = 0;
        boolean reference = false;
        for (Token token = getToken(); !token.match(Token.EOF); token = nextToken()) {
            if (token.match('*')) {
                indirections++;
                cast += "*";
            } else if (token.match('&')) {
                reference = true;
                cast += "*";
            } else if (token.match(Token.CONST)) {
                decl.constPointer = true;
                cast += "const";
            } else {
                break;
            }
        }

        int dims[] = new int[256];
        int indirections2 = 0;
        decl.objectName = defaultName;
        if (getToken().match('(')) {
            while (getToken().match('(')) { 
                nextToken();
            }
            for (Token token = getToken(); !token.match(Token.EOF); token = nextToken()) {
                if (token.match(Token.IDENTIFIER)) {
                    decl.objectName = token.value;
                } else if (token.match('*')) {
                    indirections2++;
                    decl.convention = decl.objectName;
                    decl.objectName = defaultName;
                } else if (token.match('[')) {
                    Token n = getToken(1);
                    dims[decl.indices++] = n.match(Token.INTEGER) ? Integer.parseInt(n.value) : -1;
                } else if (token.match(')')) {
                    nextToken();
                    break;
                }
            }
            while (getToken().match(')')) {
                nextToken();
            }
        } else if (getToken().match(Token.IDENTIFIER)) {
            decl.objectName = getToken().value;
            nextToken();
        }

        boolean bracket = false;
        for (Token token = getToken(); !token.match(Token.EOF); token = nextToken()) {
            if (!bracket && token.match('[')) {
                bracket = true;
                Token n = getToken(1);
                dims[decl.indices++] = n.match(Token.INTEGER) ? Integer.parseInt(n.value) : -1;
            } else if (!bracket) {
                break;
            } else if (bracket && token.match(']')) {
                bracket = false;
            }
        }
        while (decl.indices > 0 && indirections2 > 0) {
            // treat complex combinations of arrays and pointers as multidimensional arrays
            dims[decl.indices++] = -1;
            indirections2--;
        }
        if (arrayAsPointer && decl.indices > 0) {
            // treat array as an additional indirection
            indirections++;
            String dimCast = "";
            for (int i = 1; i < decl.indices; i++) {
                if (dims[i] > 0) {
                    dimCast += "[" + dims[i] + "]";
                }
            }
            //decl.indices = 0;
            cast += dimCast.length() > 0 ? "(*)" + dimCast : "*";
        }
        if (pointerAsArray && indirections > 1) {
            // treat second indirection as an array
            dims[decl.indices++] = -1;
            indirections--;
            cast = cast.substring(0, cast.length() - 1);
        }

        if (getToken().match(':')) {
            // ignore bitfields
            decl.annotations += "@NoOffset ";
            nextToken().expect(Token.INTEGER);
            nextToken().expect(',', ';');
        }

        int infoLength = 1;
        boolean valueType = false, needCast = false, implicitConst = false;
        String prefix = decl.constValue && indirections < 2 && !reference ? "const " : "";
        LinkedList<Info> infoList = infoMap.get(prefix + decl.cppType);
        String ns = "";
        while (namespace != null && infoList.size() == 0 && !ns.equals(namespace)) {
            int i = namespace.indexOf("::", ns.length() + 2);
            ns = i < 0 ? namespace : namespace.substring(0, i);
            infoList = infoMap.get(prefix + ns + "::" + decl.cppType);
        }
        if (ns.length() > 0) {
            cast = ns + "::" + cast;
        }
        if (infoList.size() > 0) {
            Info info = infoList.getFirst();
            valueType = info.valueTypes != null &&
                    ((indirections == 0 && !reference) || info.pointerTypes == null);
            needCast = info.cast;
            implicitConst = info.cppNames[0].startsWith("const ");
            infoLength = valueType ? info.valueTypes.length :
                    info.pointerTypes != null ? info.pointerTypes.length : 1;
            decl.infoNumber = infoNumber < 0 ? 0 : infoNumber % infoLength;
            decl.javaType = valueType ? info.valueTypes[decl.infoNumber] :
                    info.pointerTypes != null ? info.pointerTypes[decl.infoNumber] : decl.cppType;
        }

        if (!valueType) {
            if (indirections == 0 && !reference) {
                decl.annotations += "@ByVal ";
            } else if (indirections == 0 && reference) {
                decl.annotations += "@ByRef ";
            } else if (indirections == 1 && reference) {
                decl.annotations += "@ByPtrRef ";
            } else if (indirections == 2 && !reference && infoNumber >= 0) {
                decl.annotations += "@ByPtrPtr ";
                if (decl.cppType.equals("void")) {
                    needCast = true;
                }
            } else if (indirections >= 2) {
                decl.infoNumber += infoLength;
                needCast = true;
                decl.javaType = "PointerPointer";
                if (reference) {
                    decl.annotations += "@ByRef ";
                }
            }

            if (!needCast && decl.constValue && !implicitConst) {
                decl.annotations = "@Const " + decl.annotations;
            }
        }
        if (needCast || (arrayAsPointer && decl.indices > 1)) {
            if (decl.constValue) {
                cast = "const " + cast;
            }
            if (!valueType && indirections == 0 && !reference) {
                decl.annotations += "@Cast(\"" + cast + "*\") ";
            } else {
                decl.annotations = "@Cast(\"" + cast + "\") " + decl.annotations;
            }
        }

        Parameters params = parameters(namespace, typeMap, infoNumber);
        if (params != null) {
            decl.infoNumber = Math.max(decl.infoNumber, params.infoNumber);
            if (params.definitions.length() > 0) {
                decl.definitions += params.definitions + "\n";
            }
            if (indirections2 == 0) {
                decl.parameters = params.list;
            } else {
                String functionType = isTypedef ? decl.objectName :
                        Character.toUpperCase(decl.objectName.charAt(0)) + decl.objectName.substring(1) + params.signature;
                if (infoNumber <= params.infoNumber) {
                    decl.definitions +=
                            "public static class " + functionType + " extends FunctionPointer {\n" +
                            "    static { Loader.load(); }\n" +
                            "    public    " + functionType + "(Pointer p) { super(p); }\n" +
                            "    protected " + functionType + "() { allocate(); }\n" +
                            "    private native void allocate();\n" +
                            "    public native " + decl.annotations + decl.javaType + " call" + params.list + ";\n" +
                            "}\n";
                }
                decl.annotations = "";
                decl.javaType = functionType;
                decl.parameters = "";
            }
        }

        infoList = infoMap.get(decl.objectName);
        if (infoList.size() > 0) {
            Info info = infoList.getFirst();
            if (info.javaNames != null && info.javaNames.length > 0) {
                decl.annotations += "@Name(\"" + decl.objectName + "\") ";
                decl.objectName = info.javaNames[0];
            }
        }
        return decl;
    }

    String commentBefore() throws Exception {
        String comment = "";
        while (tokenIndex > 0 && getToken(-1, false).match(Token.COMMENT)) {
            tokenIndex--;
        }
        for (Token token = getToken(0, false); token.match(Token.COMMENT); token = nextToken(false)) {
            if (token.value.length() <= 3 || token.value.charAt(3) != '<') {
                comment += token.spacing + token.value;
            }
        }
        return comment;
    }

    String commentAfter() throws Exception {
        String comment = "";
        while (tokenIndex > 0 && getToken(-1, false).match(Token.COMMENT)) {
            tokenIndex--;
        }
        for (Token token = getToken(0, false); token.match(Token.COMMENT); token = nextToken(false)) {
            if (token.value.length() > 3 && token.value.charAt(3) == '<') {
                comment += (comment.length() > 0 ? " * " : "/**") + token.value.substring(4);
            }
        }
        if (comment.length() > 0) {
            if (!comment.endsWith("*/")) {
                comment += " */";
            }
            comment += "\n";
        }
        return comment;
    }

    boolean attribute() throws Exception {
        if (!getToken().match(Token.IDENTIFIER)) {
            return false;
        }
        if (!nextToken().match('(')) {
            return true;
        }

        int count = 1;
        for (Token token = nextToken(); !token.match(Token.EOF) && count > 0; token = nextToken(false)) {
            if (token.match('(')) {
                count++;
            } else if (token.match(')')) {
                count--;
            }
        }
        return true;
    }

    boolean body() throws Exception {
        if (!getToken().match('{')) {
            return false;
        }

        int count = 1;
        for (Token token = nextToken(); !token.match(Token.EOF) && count > 0; token = nextToken(false)) {
            if (token.match('{')) {
                count++;
            } else if (token.match('}')) {
                count--;
            }
        }
        return true;
    }


    static class Parameters {
        int infoNumber = 0;
        String list = "", definitions = "", signature = "", names = "";
    }

    Parameters parameters(String namespace, TemplateMap templateMap, int infoNumber) throws Exception {
        if (!getToken().match('(')) {
            return null;
        }

        int count = 0;
        Parameters params = new Parameters();
        params.list = "(";
        params.names = "(";
        for (Token token = nextToken(); !token.match(Token.EOF); token = getToken()) {
            String spacing = token.spacing;
            if (token.match(')')) {
                params.list += spacing + ")";
                params.names += ")";
                nextToken();
                break;
            }
            Declarator decl = declarator(namespace, templateMap, "arg" + count++, infoNumber, 0, true, false);
            if (decl != null && !decl.javaType.equals("void")) {
                params.infoNumber = Math.max(params.infoNumber, decl.infoNumber);
                params.list += (count > 1 ? "," : "") + spacing + decl.annotations + decl.javaType + " " + decl.objectName;
                params.definitions += decl.definitions;
                params.signature += '_';
                for (char c : decl.javaType.substring(decl.javaType.lastIndexOf(' ') + 1).toCharArray()) {
                    if (Character.isDigit(c) || Character.isLetter(c) || c == '_') {
                        params.signature += c;
                    }
                }
                params.names += (count > 1 ? ", " : "") + decl.objectName;
                if (decl.objectName.startsWith("arg")) {
                    try {
                        count = Integer.parseInt(decl.objectName.substring(3)) + 1;
                    } catch (NumberFormatException e) { /* don't care if not int */ }
                }
            }
            if (getToken().match('=')) {
                params.list += "/*=" + nextToken();
                for (token = nextToken(); !token.match(Token.EOF); token = nextToken()) {
                    if (token.match(',', ')')) {
                        break;
                    }
                    params.list += token.spacing + token;
                }
                params.list += "*/";
            }
            if (getToken().expect(',', ')').match(',')) {
                nextToken();
            }
        }
        return params;
    }

    String function(String namespace, String group, TemplateMap templateMap) throws Exception {
        return function(namespace, group, templateMap, false);
    }
    String function(String namespace, String group, TemplateMap templateMap, boolean constructor) throws Exception {
        int backIndex = tokenIndex;
        boolean destructor = false;
        String spacing = getToken().spacing;
        String access = group == null || group.length() == 0 ? "public static native " : "public native ";
        for (Token token = getToken(); !token.match(Token.EOF); token = nextToken()) {
            if (token.match(Token.STATIC)) {
                access = "public static native ";
            } else if (token.match('~')) {
                destructor = true;
            } else if (!token.match(Token.INLINE, Token.VIRTUAL)) {
                break;
            }
        }
        int startIndex = tokenIndex;
        Parameters params = null;
        Declarator decl = null;
        String name = null;
        if (constructor || destructor) {
            name = getToken().value;
            nextToken();
            params = parameters(namespace, templateMap, 0);
            if (!name.equals(group) || params == null) {
                tokenIndex = backIndex;
                return null;
            }
            decl = new Declarator();
            decl.parameters = params.list;
            decl.definitions = params.definitions;
        } else {
            decl = declarator(namespace, templateMap, null, 0, 0, false, false);
            name = decl.objectName;
        }
        if (name == null || decl.parameters.length() == 0) {
            tokenIndex = backIndex;
            return null;
        }

        String text  = "";
        String definitions = "";
        LinkedList<Info> infoList = infoMap.get(name);
        if (infoList.size() == 0) {
            infoList.add(null);
        }
        for (Info info : infoList) {
            if (info != null) {
                if (info.genericTypes != null && templateMap != null) {
                    int count = 0;
                    for (String key : templateMap.keySet()) {
                        if (count < info.genericTypes.length) {
                            templateMap.put(key, info.genericTypes[count++]);
                        }
                    }
                }
                name = info.javaNames == null ? info.cppNames[0] : info.javaNames.length == 0 ? "" : info.javaNames[0];
                if (!name.equals(info.cppNames[0]) && name.length() > 0) {
                    name = "@Name(\"" + info.cppNames[0] + "\") " + name;
                }
            }

            LinkedList<Declarator> prevDecl = new LinkedList<Declarator>();
            for (int n = -1; n < Integer.MAX_VALUE; n++) {
                tokenIndex = startIndex;
                if (constructor || destructor) {
                    name = getToken().value;
                    nextToken();
                    params = parameters(namespace, templateMap, n);
                    decl.parameters = params.list;
                    decl.definitions = params.definitions;
                    if (getToken().match(':')) {
                        for (Token token = nextToken(); !token.match(Token.EOF); token = nextToken()) {
                            if (token.match('}', ';')) {
                                break;
                            }
                        }
                    }
                } else {
                    decl = declarator(namespace, templateMap, null, n, 0, false, false);
                }
                boolean found = false;
                for (Declarator d : prevDecl) {
                    found |= /* decl.javaType.equals(d.javaType) && */ decl.parameters.equals(d.parameters);
                }
                if (found && n > 0) {
                    break;
                }
                if (name.length() > 0 && !found && !destructor) {
                    if (namespace != null && group == null) {
                        text += "@Namespace(\"" + namespace + "\") ";
                    }
                    if (constructor) {
                        text += "public " + name + decl.parameters + " { allocate" + params.names + "; }\n" +
                                "private native void allocate" + decl.parameters + ";\n";
                    } else {
                        text += access + decl.annotations + decl.javaType + " " + name + decl.parameters + ";\n";
                    }
                    definitions += decl.definitions;
                }
                prevDecl.add(decl);
            }
            while (attribute()) { }
            if (getToken().match('{')) {
                body();
            } else {
                nextToken();
            }
        }
        String comment = commentAfter();
        return rescan(definitions + comment + text, spacing);
    }

    String variable(String namespace, String group) throws Exception {
        int backIndex = tokenIndex;
        String spacing = getToken().spacing;
        Declarator decl = declarator(namespace, null, null, 0, 0, false, true);
        String name = decl.objectName;
        if (name == null || !getToken().match('[', '=', ',', ':', ';')) {
            tokenIndex = backIndex;
            return null;
        }

        String text  = "";
        String definitions = "";
        for (int n = 0; n < Integer.MAX_VALUE; n++) {
            tokenIndex = backIndex;
            decl = declarator(namespace, null, null, -1, n, false, true);
            if (decl == null) {
                break;
            }
            String access = group == null || group.length() == 0 ? "public static native " : "public native ";
            String setterType = group == null || group.length() == 0 ? "void " : group + " ";
            name = decl.objectName;
            String indices = "";
            for (int i = 0; i < decl.indices; i++) {
                if (i > 0) {
                    indices += ", ";
                }
                indices += "int " + (char)('i' + i);
            }
            if (namespace != null && group == null) {
                text += "@Namespace(\"" + namespace + "\") ";
            }
            if (decl.constValue) {
                text += "@MemberGetter ";
            }
            text += access + decl.annotations + decl.javaType + " " + name + "(" + indices + ");";
            if (!decl.constValue) {
                if (decl.indices > 0) {
                    indices += ", ";
                }
                text += " " + access + setterType + name + "(" + indices + decl.javaType + " " + name + ");";
            }
            text += "\n";
            definitions += decl.definitions;

            if (decl.indices > 0) {
                // in the case of arrays, also add a pointer accessor
                tokenIndex = backIndex;
                decl = declarator(namespace, null, null, -1, n, true, false);
                if (namespace != null && group == null) {
                    text += "@Namespace(\"" + namespace + "\") ";
                }
                text += "@MemberGetter " + access + decl.annotations + decl.javaType + " " + name + "();\n";
            }
        }
        String comment = commentAfter();
        return rescan(definitions + comment + text, spacing);
    }

    String macro() throws Exception {
        if (!getToken().match('#')) {
            return null;
        }
        String spacing = getToken().spacing;
        Token keyword = nextToken();

        nextToken();
        int beginIndex = tokenIndex;
        for (Token token = getToken(); !token.match(Token.EOF); token = nextToken(false)) {
            if (token.match(Token.COMMENT) || token.spacing.indexOf('\n') >= 0) {
                break;
            }
        }
        int endIndex = tokenIndex;

        String text = "";
        if (keyword.match(Token.DEFINE) && beginIndex + 1 < endIndex) {
            tokenIndex = beginIndex;
            String name = getToken().value;
            Token first = nextToken();
            LinkedList<Info> infoList = infoMap.get(name);
            if (first.spacing.length() == 0 && first.match('(')) {
                // declare as a static native methods
                for (Info info : infoList) {
                    if (info.genericTypes == null || info.genericTypes.length == 0) {
                        continue;
                    }

                    int count = 1;
                    tokenIndex = beginIndex + 2;
                    String type = "", params = "(";
                    for (Token token = getToken(); tokenIndex < endIndex &&
                            count < info.genericTypes.length; token = nextToken()) {
                        if (token.match(Token.IDENTIFIER)) {
                            type = info.genericTypes[count];
                            name = token.value;
                            if (name.equals("...")) {
                                name = "arg" + count;
                            }
                            params += type + " " + name;
                            if (++count < info.genericTypes.length) {
                                params += ", ";
                            }
                        } else if (token.match(')')) {
                            break;
                        }
                    }
                    while (count < info.genericTypes.length) {
                        type = info.genericTypes[count];
                        name = "arg" + count;
                        params += type + " " + name;
                        if (++count < info.genericTypes.length) {
                            params += ", ";
                        }
                    }
                    params += ")";

                    type = info.genericTypes[0];
                    name = info.javaNames == null ? info.cppNames[0] : info.javaNames[0];
                    if (!name.equals(info.cppNames[0])) {
                        name = "@Name(\"" + info.cppNames[0] + "\") " + name;
                    }
                    text += "public static native " + type + " " + name + params + ";\n";
                }
            } else if (infoList.size() == 0 ||
                       infoList.getFirst().genericTypes == null ||
                       infoList.getFirst().genericTypes.length > 0) {
                // declare as a static final variable
                String value = "";
                String type = "int";
                String cat = "";
                tokenIndex = beginIndex + 1;
                Token prevToken = new Token();
                boolean complex = false;
                for (Token token = getToken(); tokenIndex < endIndex; token = nextToken()) {
                    if (token.match(Token.STRING)) {
                        type = "String"; cat = " + "; break;
                    } else if (token.match(Token.FLOAT)) {
                        type = "double"; cat = ""; break;
                    } else if (token.match(Token.INTEGER) && token.value.endsWith("L")) {
                        type = "long"; cat = ""; break;
                    } else if ((prevToken.match(Token.IDENTIFIER) && token.match('(')) || token.match('{', '}')) {
                        complex = true;
                    }
                    prevToken = token;
                }
                if (infoList.size() > 0) {
                    Info info = infoList.getFirst();
                    if (info.genericTypes != null) {
                        type = info.genericTypes[0];
                    }
                    name = info.cppNames[0];
                    if (info.javaNames != null) {
                        name = info.javaNames[0];
                    }
                    if (info.complex) {
                        complex = true;
                    }
                }
                tokenIndex = beginIndex + 1;
                if (complex) {
                    text += "public static native @MemberGetter " + type + " " + name + "();\n";
                    value = " " + name + "()";
                } else {
                    for (Token token = getToken(); tokenIndex < endIndex; token = nextToken()) {
                        value += token.spacing + token + (tokenIndex < endIndex ? cat : "");
                    }
                }
                int i = type.lastIndexOf(' ');
                if (i >= 0) {
                    type = type.substring(i + 1);
                }
                text += "public static final " + type + " " + name + " =" + value + ";\n";
            }
            tokenIndex = endIndex;
            String comment = commentAfter();
            text = rescan(comment + text, spacing);
        } else if (keyword.match(Token.IF, Token.IFDEF, Token.IFNDEF, Token.ELIF) && beginIndex < endIndex) {
            tokenIndex = beginIndex;
            String value = "";
            for (Token token = getToken(); tokenIndex < endIndex; token = nextToken()) {
                value += token.spacing + token;
            }
            text = spacing + "// #" + keyword + value;
            LinkedList<Info> infoList = infoMap.get(value);
            boolean define = true;
            if (infoList.size() > 0) {
                Info info = infoList.getFirst();
                define = keyword.match(Token.IFNDEF) ? !info.define : info.define;
            }
            if (!define) {
                int count = 1;
                Token prevToken = new Token();
                for (Token token = getToken(); !token.match(Token.EOF) && count > 0; token = nextToken(false)) {
                    if (prevToken.match('#') && token.match(Token.IF, Token.IFDEF, Token.IFNDEF)) {
                        count++;
                    } else if (prevToken.match('#') && token.match(Token.ELIF, Token.ELSE, Token.ENDIF)) {
                        count--;
                    }
                    prevToken = token;
                }
                tokenIndex -= 2;
            }
        } else {
            tokenIndex = beginIndex;
            text = spacing + "// #" + keyword;
            for (Token token = getToken(); tokenIndex < endIndex; token = nextToken()) {
                text += token.spacing + token;
            }
        }
        return text;
    }

    String typedef(String namespace) throws Exception {
        if (!getToken().match(Token.TYPEDEF)) {
            return null;
        }
        String spacing = getToken().spacing;
        Declarator decl = declarator(namespace, null, null, 0, 0, true, false);
        nextToken();

        String name = decl.objectName;
        if (namespace != null) {
            name = namespace + "::" + name;
        }
        if (decl.definitions.length() > 0) {
            infoMap.put(new Info(name).valueTypes(decl.objectName));
        } else {
            LinkedList<Info> infoList = infoMap.get(decl.cppType);
            Info info = infoList.size() > 0 ? infoList.getFirst().clone() : new Info(name);
            if (info.valueTypes == null) {
                info.valueTypes(info.cppNames);
            }
            if (info.pointerTypes == null) {
                info.pointerTypes(info.cppNames);
            }
            infoMap.put(info.cppNames(name).cast(true));
        }

        String comment = commentAfter();
        return rescan(comment + decl.definitions, spacing);
    }

    String group(String namespace, TemplateMap templateMap) throws Exception {
        int backIndex = tokenIndex;
        String spacing = getToken().spacing;
        boolean isTypedef = getToken().match(Token.TYPEDEF);
        boolean foundGroup = false, accessible = true;
        for (Token token = getToken(); !token.match(Token.EOF); token = nextToken()) {
            if (token.match(Token.CLASS, Token.STRUCT, Token.UNION)) {
                foundGroup = true;
                accessible = !token.match(Token.CLASS);
                break;
            } else if (!token.match(Token.IDENTIFIER)) {
                break;
            }
        }
        if (!foundGroup) {
            tokenIndex = backIndex;
            return null;
        }

        if (!getToken(1).match('{') && getToken(2).match(Token.IDENTIFIER)
                && (isTypedef || !getToken(3).match(';'))) {
            nextToken();
        }
        String text = "";
        String name = nextToken().expect(Token.IDENTIFIER, '{').value;
        String parent = "Pointer";
        if (getToken(0).match(Token.IDENTIFIER) && getToken(1).match(':')) {
            for (Token token = nextToken(); !token.match(Token.EOF); token = nextToken()) {
                if (token.match('{')) {
                    break;
                } else if (token.match(Token.PUBLIC)) {
                    parent = nextToken().expect(Token.IDENTIFIER).value;
                }
            }
        }
        if (!getToken(0).match('{', ';') && !getToken(1).match('{', ';')) {
            tokenIndex = backIndex;
            return null;
        }
        LinkedList<Info> infoList = infoMap.get(name);
        if (getToken().match(Token.IDENTIFIER) && nextToken().match(';')) {
            nextToken();
            if (infoList.size() == 0 || infoList.getFirst().opaque) {
                infoMap.put(new Info(name).opaque(false));
                if (namespace != null) {
                    text += "@Namespace(\"" + namespace + "\") ";
                }
                text += "@Opaque public static class " + name + " extends " + parent + " {\n" +
                        "    public " + name + "() { }\n" +
                        "    public " + name + "(Pointer p) { super(p); }\n" +
                        "}";
            }
            String comment = commentAfter();
            return rescan(comment + text, spacing);
        }
        int index = tokenIndex;
        if (body() && isTypedef && getToken().match(Token.IDENTIFIER)) {
            for (Token token = getToken(); !token.match(Token.EOF); token = nextToken()) {
                if (!token.match(Token.IDENTIFIER)) {
                    text += token.expect(';').spacing;
                    break;
                }
                name = token.value;
            }
            infoList = infoMap.get(name);
        }
        if (name.length() == 0) {
            // XXX: This is a variable declaration with anonymous type
            for (Token token = nextToken(); !token.match(Token.EOF); token = nextToken()) {
                if (token.match(';')) {
                    nextToken();
                    break;
                }
            }
            return "";
        }
        tokenIndex = index;

        String ns = namespace == null ? name : namespace + "::" + name;
        String declarations = "";
        boolean implicitConstructor = true, defaultConstructor = false;
        if (getToken().match('{')) {
            nextToken();
        }
        for (Token token = getToken(); !token.match(Token.EOF, '}'); token = getToken()) {
            if (token.match(Token.PRIVATE, Token.PROTECTED, Token.PUBLIC) && nextToken().match(':')) {
                accessible = token.match(Token.PUBLIC);
                nextToken();
            }
            String t = function(ns, name, templateMap, true);
            if (t == null) {
                t = declaration(ns, name, templateMap);
            } else if (accessible) {
                implicitConstructor = false;
                if (t.contains("allocate()")) {
                    defaultConstructor = true;
                }
            }
            if (accessible) {
                declarations += t;
            }
        }

        text += spacing;
        if (namespace != null) {
            text += "@Namespace(\"" + namespace + "\") ";
        }
        if (!implicitConstructor) {
            text += "@NoOffset ";
        }
        text += "public static class " + name + " extends " + parent + " {\n" +
                "    static { Loader.load(); }\n";

        if (implicitConstructor) {
            text += "    public " + name + "() { allocate(); }\n" +
                    "    public " + name + "(int size) { allocateArray(size); }\n" +
                    "    public " + name + "(Pointer p) { super(p); }\n" +
                    "    private native void allocate();\n" +
                    "    private native void allocateArray(int size);\n" +
                    "    @Override public " + name + " position(int position) {\n" +
                    "        return (" + name + ")super.position(position);\n" +
                    "    }\n";
        } else {
            if (!defaultConstructor) {
                text += "    public " + name + "() { }\n";
            }
            text += "    public " + name + "(Pointer p) { super(p); }\n";
        }

        text += declarations + getToken().spacing + '}';
        for (Token token = nextToken(); !token.match(Token.EOF); token = nextToken()) {
            if (!token.match(Token.IDENTIFIER)) {
                text += token.expect(';').spacing;
                break;
            }
        }
        if (namespace != null) {
            name = namespace + "::" + name;
        }
        infoMap.put(new Info(name));
        nextToken();
        return text;
    }

    String enumeration(String namespace) throws Exception {
        int backIndex = tokenIndex;
        String enumSpacing = getToken().spacing;
        boolean isTypedef = getToken().match(Token.TYPEDEF);
        boolean foundEnum = false;
        for (Token token = getToken(); !token.match(Token.EOF); token = nextToken()) {
            if (token.match(Token.ENUM)) {
                foundEnum = true;
                break;
            } else if (!token.match(Token.IDENTIFIER)) {
                break;
            }
        }
        if (!foundEnum) {
            tokenIndex = backIndex;
            return null;
        }

        if (isTypedef && !getToken(1).match('{') && getToken(2).match(Token.IDENTIFIER)) {
            nextToken();
        }
        boolean first = true;
        int count = 0;
        String countPrefix = " ";
        String enumerators = "";
        String macroText = "";
        String name = nextToken().expect(Token.IDENTIFIER, '{').value;
        if (!getToken().match('{') && !nextToken().match('{')) {
            tokenIndex = backIndex;
            return null;
        }
        for (Token token = nextToken(); !token.match(Token.EOF, '}'); token = getToken()) {
            String s = macro();
            if (s != null) {
                macroText += s;
                continue;
            }
            String comment = commentBefore();
            Token enumerator = getToken();
            String spacing2 = " ";
            String separator = first ? "" : ",";
            if (nextToken().match('=')) {
                spacing2 = getToken().spacing;
                countPrefix = " ";
                int count2 = 0;
                Token prevToken = new Token();
                boolean complex = false;
                for (token = nextToken(); !token.match(Token.EOF, ',', '}') || count2 > 0; token = nextToken()) {
                    countPrefix += token.spacing + token;
                    if (token.match('(')) {
                        count2++;
                    } else if (token.match(')')) {
                        count2--;
                    }
                    if (prevToken.match(Token.IDENTIFIER) && token.match('(')) {
                        complex = true;
                    }
                    prevToken = token;
                }
                try {
                    count = Integer.parseInt(countPrefix.trim());
                    countPrefix = " ";
                } catch (NumberFormatException e) {
                    count = 0;
                    if (complex) {
                        if (!first) {
                            separator = ";\n";
                            first = true;
                        }
                        separator += "public static native @MemberGetter int " + enumerator.value + "();\npublic static final int";
                        countPrefix = " " + enumerator.value + "()";
                    }
                }
            }
            first = false;
            enumerators += separator + macroText + comment;
            macroText = "";
            comment = commentAfter();
            if (comment.length() == 0 && getToken().match(',')) {
                nextToken();
                comment = commentAfter();
            }
            String spacing = enumerator.spacing;
            if (comment.length() > 0) {
                enumerators += spacing + comment;
                int newline = spacing.lastIndexOf('\n');
                if (newline >= 0) {
                    spacing = spacing.substring(newline + 1);
                }
            }
            enumerators += spacing + enumerator.value + spacing2 + "=" + countPrefix;
            if (countPrefix.trim().length() > 0) {
                if (count > 0) {
                    enumerators += " + " + count;
                }
            } else {
                enumerators += count;
            }
            count++;
        }
        String comment = commentBefore();
        String text = "";
        Token token = nextToken();
        if (token.match(Token.IDENTIFIER)) {
            // XXX: If !isTypedef, this is a variable declaration with anonymous type
            name = token.value;
            token = nextToken();
        }
        if (namespace != null) {
            name = namespace + "::" + name;
        }
        text += enumSpacing + "/** enum " + name + " */\n";
        int newline = enumSpacing.lastIndexOf('\n');
        if (newline >= 0) {
            enumSpacing = enumSpacing.substring(newline + 1);
        }
        text += enumSpacing + "public static final int" + enumerators + token.expect(';').spacing + ";";
        infoMap.put(new Info(name).valueTypes("int").pointerTypes("IntPointer", "IntBuffer", "int[]").cast(true));
        nextToken();
        return text + macroText + comment;
    }

    String namespace(String namespace) throws Exception {
        if (!getToken().match(Token.NAMESPACE)) {
            return null;
        }
        String name = nextToken().expect(Token.IDENTIFIER).value;
        nextToken().expect('{');
        nextToken();

        String text = "";
        while (!getToken().match(Token.EOF, '}')) {
            text += declaration(namespace == null ? name : namespace + "::" + name, null, null);
        }
        text += getToken().spacing;
        nextToken();
        return text;
    }

    String extern(String namespace) throws Exception {
        if (!getToken(0).match(Token.EXTERN) || !getToken(1).match(Token.STRING)) {
            return null;
        }
        nextToken().expect("\"C\"");
        if (!nextToken().match('{')) {
            return "";
        }
        nextToken();

        String text = "";
        while (!getToken().match(Token.EOF, '}')) {
            text += declaration(namespace, null, null);
        }
        nextToken();
        return text;
    }

    String declaration(String namespace, String group, TemplateMap templateMap) throws Exception {
        String comment = commentBefore(), text;
        Token token = getToken();
        String spacing = token.spacing;
        TemplateMap map = template(templateMap);
        if (map != templateMap) {
            comment += spacing.substring(0, spacing.lastIndexOf('\n'));
        }
        if ((text = macro())                         != null) { return comment + text; }
        if ((text = extern(namespace))               != null) { return comment + text; }
        if ((text = namespace(namespace))            != null) { return comment + text; }
        if ((text = enumeration(namespace))          != null) { return comment + text; }
        if ((text = group(namespace, map))           != null) { return comment + text; }
        if ((text = typedef(namespace))              != null) { return comment + text; }
        if ((text = function(namespace, group, map)) != null) { return comment + text; }
        if ((text = variable(namespace, group))      != null) { return comment + text; }
        if (attribute()                                     ) { return comment + spacing; }
        throw new Exception(token.file + ":" + token.lineNumber + ": Could not parse declaration at '" + token + "'");
    }

    public void parse(String outputFilename, String ... inputFilenames) throws IOException, Exception {
        File[] files = new File[inputFilenames.length];
        for (int i = 0; i < files.length; i++) {
            files[i] = new File(inputFilenames[i]);
        }
        parse(new File(outputFilename), files);
    }
    public void parse(File outputFile, File ... inputFiles) throws IOException, Exception {
        ArrayList<Token> tokens = new ArrayList<Token>();
        String lineSeparator = "\n";
        for (File file : inputFiles) {
            LinkedList<Info> infoList = infoMap.get(file.getName());
            if (infoList.size() > 0 && !infoList.getFirst().parse) {
                continue;
            }
            System.out.println("Parsing header file: " + file);
            Token token = new Token();
            token.type = Token.COMMENT;
            token.value = "\n/* Wrapper for header file " + file + " */\n\n";
            tokens.add(token);
            Tokenizer tokenizer = new Tokenizer(file);
            while (!(token = tokenizer.nextToken()).isEmpty()) {
                if (token.type == -1) {
                    token.type = Token.COMMENT;
                }
                tokens.add(token);
            }
            if (lineSeparator == null) {
                lineSeparator = tokenizer.lineSeparator;
            }
            tokenizer.close();
            token = new Token();
            token.type = Token.COMMENT;
            token.spacing = "\n";
            tokens.add(token);
        }
        tokenArray = tokens.toArray(new Token[tokens.size()]);
        tokenIndex = 0;

        Writer out = outputFile != null ? new FileWriter(outputFile) : new Writer() {
            @Override public void write(char[] cbuf, int off, int len) { }
            @Override public void flush() { }
            @Override public void close() { }
        };
        LinkedList<Info> infoList = infoMap.get(null);
        for (Info info : infoList) {
            if (info.textBefore != null) {
                out.append(info.textBefore.replaceAll("\n", lineSeparator));
            }
        }
        out.append("{" + lineSeparator);
        out.append("    static { Loader.load(); }" + lineSeparator);
        for (Info info : infoList) {
            if (info.textAfter != null) {
                out.append(info.textAfter.replaceAll("\n", lineSeparator));
            }
        }
        out.append(vectors());
        while (!getToken().match(Token.EOF)) {
            out.append(declaration(null, null, null).replaceAll("\n", lineSeparator));
        }
        String comment = commentBefore();
        if (comment != null) {
            out.append(comment.replaceAll("\n", lineSeparator));
        }
        out.append(lineSeparator + "}" + lineSeparator);
        out.close();
    }

    public File parse(String outputDirectory, Class cls) throws IOException, Exception {
        return parse(new File(outputDirectory), cls);
    }
    public File parse(File outputDirectory, Class cls) throws IOException, Exception {
        Loader.ClassProperties allProperties = Loader.loadProperties(cls, properties, true);
        Loader.ClassProperties clsProperties = Loader.loadProperties(cls, properties, false);
        LinkedList<File> allFiles = allProperties.getHeaderFiles();
        LinkedList<File> clsFiles = clsProperties.getHeaderFiles();
        LinkedList<String> allTargets = allProperties.get("parser.target");
        LinkedList<String> clsTargets = clsProperties.get("parser.target");
        String target = clsTargets.getFirst(); // there can only be one

        String text = "/* DO NOT EDIT THIS FILE - IT IS MACHINE GENERATED */\n\n";
        int n = target.lastIndexOf('.');
        if (n >= 0) {
            text += "package " + target.substring(0, n) + ";\n\n";
        }
        text += "import com.googlecode.javacpp.*;\n" +
                "import com.googlecode.javacpp.annotation.*;\n" +
                "import java.nio.*;\n\n";
        for (String s : allTargets) {
            if (!target.equals(s)) {
                text += "import static " + s + ".*;\n";
            }
        }
        if (allTargets.size() > 1) {
            text += "\n";
        }
        text += "public class " + target.substring(n + 1) + " extends " + cls.getCanonicalName() + " ";
        infoMap.put(new Info().textBefore(text));

        File targetFile = new File(outputDirectory, target.replace('.', '/') + ".java");
        System.out.println("Targeting file: " + targetFile);
        for (File f : allFiles) {
            if (!clsFiles.contains(f)) {
                parse(null, f);
            }
        }
        parse(targetFile, clsFiles.toArray(new File[clsFiles.size()]));
        return targetFile;
    }
}