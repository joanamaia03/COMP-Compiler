package pt.up.fe.comp2024.optimization;

import org.antlr.v4.runtime.misc.Pair;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.Kind;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;
import java.util.Spliterator;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }


    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(NEW_CLASS_EXPR, this::visitNewClass);
        addVisit(FUNCTION_EXPR, this::visitFuncExpr);
        addVisit(NEG_EXPR, this::visitNegExpr);
        addVisit(BOOL_LITERAL, this::visitBoolLiteral);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitBoolLiteral(JmmNode jmmNode, Void unused) {
        var type = new Type("boolean", false);
        String boolType = OptUtils.toOllirType(type);
        if (jmmNode.get("value").equals("true")) {
            return new OllirExprResult("1" + boolType);
        } else {
            return new OllirExprResult("0" + boolType);
        }

    }

    public OllirExprResult visitNegExpr(JmmNode jmmNode, Void unused) {

        StringBuilder computation = new StringBuilder();
        String temp = "t" + OptUtils.getNextTempNum() + ".bool";
        computation.append(temp);
        computation.append(ASSIGN);
        computation.append(".bool ");
        computation.append("!.bool ");
        computation.append("1.bool");
        computation.append(END_STMT);

        return new OllirExprResult(temp, computation.toString());
    }

    public OllirExprResult visitFuncExpr(JmmNode jmmNode, Void unused) {
        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();

        var importName = jmmNode.getChild(0);
        String invokeName = jmmNode.get("value");
        String varRef = importName.hasAttribute("value") ? importName.get("value") : importName.get("name");
        String retType = "";
        String typeName = "";

        // Se o método não for chamado com 'this', determinamos o tipo de classe do qual o método é chamado
        if(!varRef.equals("this")){
            typeName = TypeUtils.getVarExprType(importName,table).getName();
        }
        var temp = OptUtils.getTemp();

        // Verifica se a classe do método é a mesma que a classe atual ou se é uma classe importada
        if (varRef.equals("this") || TypeUtils.getVarExprType(importName,table).getName().equals(table.getClassName()) || table.getImports().contains(TypeUtils.getVarExprType(importName,table).getName())) {
            computation.append(temp);
            var node = jmmNode;
            //Procura o método dentro da classe atual
            while (!node.getKind().equals("ClassDecl")) {
                node = node.getParent();
            }
            var listMethod = node.getChildren("MethodDecl").stream().filter(method -> method.get("name").equals(invokeName)).toList();
            if (!listMethod.isEmpty()) {
                var first = listMethod.get(0);
                //PROBLEMA: ele vai buscar o tipo do foo em vez do tipo do c
                retType = OptUtils.toOllirType(first.getChild(0).get("name"));
            }
            computation.append(retType);
            computation.append(ASSIGN);
            computation.append(retType);
            computation.append(SPACE);
            computation.append("invokevirtual").append("(");
        }
        else {
            computation.append("invokestatic").append("(");
            typeName = "";
            retType = ".V";
        }

        // Adiciona a referência do método e seu nome à computação
        if (varRef.equals("this")) {
            computation.append("this");
        }
        else {
            computation.append(importName.get("name"));
            if(!typeName.equals("")){
                computation.append(".");
            }
            computation.append(typeName);
        }

        computation.append(", \"");
        computation.append(invokeName);

        // Adiciona os argumentos do método
        for (int i = 1; i < jmmNode.getNumChildren(); i++) {
            var nodeResult = visit(jmmNode.getJmmChild(i));
            computation.append(nodeResult.getComputation());
            code.append(nodeResult.getCode());
            if (i != jmmNode.getNumChildren() - 1) {
                code.append(",");
            }
        }
        computation.append("\"");

        if(jmmNode.getNumChildren()>1){
            computation.append(",");
            computation.append(SPACE);
        }
        computation.append(code);

        computation.append(")");
        computation.append(retType);
        computation.append(END_STMT);

        var finalee = temp + retType;

        return new OllirExprResult(finalee, computation.toString());
    }


    private OllirExprResult visitNewClass(JmmNode jmmNode, Void unused) {
        StringBuilder computation = new StringBuilder();

        var temp = OptUtils.getTemp();

        computation.append(temp);
        computation.append(OptUtils.toOllirType(jmmNode.get("className")));
        computation.append(ASSIGN);
        computation.append(OptUtils.toOllirType(jmmNode.get("className")));
        computation.append(SPACE);

        computation.append("new ");
        computation.append("(");
        computation.append(jmmNode.get("className"));
        computation.append(")");
        computation.append(OptUtils.toOllirType(jmmNode.get("className")));
        computation.append(END_STMT);

        computation.append("invokespecial(");
        computation.append(temp);
        computation.append(OptUtils.toOllirType(jmmNode.get("className")));
        computation.append(",");
        computation.append("\"<init>\"");
        computation.append(").V");
        computation.append(END_STMT);

        var finale = temp + OptUtils.toOllirType(jmmNode.get("className"));

        return new OllirExprResult(finale , computation.toString());
    }


    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }


    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();
        String code;

        System.out.println(node.getJmmChild(0));

        if (node.get("op").equals("&&")) {

            int temp = OptUtils.getNextTempNum();

            code = OptUtils.getTemp() + ".bool";

            computation.append(lhs.getComputation());

            computation.append("if").append("(").append(lhs.getCode()).append(")").append(" goto ").append("L_true").append(temp).append(";\n");

            computation.append(code).append(SPACE).append(ASSIGN).append(".bool").append(SPACE).append("0.bool").append(END_STMT);

            computation.append("goto ").append("L_end").append(temp).append(";\n");

            computation.append("L_true").append(temp).append(":\n").append(rhs.getComputation());

            computation.append(code).append(SPACE).append(ASSIGN).append(".bool").append(SPACE).append(rhs.getCode()).append(END_STMT);

            computation.append("L_end").append(temp).append(":\n");

        } else {
            // code to compute the children
            computation.append(lhs.getComputation());
            computation.append(rhs.getComputation());

            // code to compute self
            Type resType = TypeUtils.getExprType(node, table);
            String resOllirType = OptUtils.toOllirType(resType);
            code = OptUtils.getTemp() + resOllirType;

            computation.append(code).append(SPACE)
                    .append(ASSIGN).append(resOllirType).append(SPACE)
                    .append(lhs.getCode()).append(SPACE);

            Type type = TypeUtils.getExprType(node, table);
            computation.append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                    .append(rhs.getCode()).append(END_STMT);
        }

        return new OllirExprResult(code, computation);
    }


    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        StringBuilder computation = new StringBuilder();
        StringBuilder code = new StringBuilder();
        boolean param = false;
        int pos = 0;
        for(int i=0; i< table.getParameters(node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow()).size();i++){
            if (table.getParameters(node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow()).get(i).getName().equals(node.get("name"))) {
                param = true;
                pos = i + 1;
                break;
            }
        }
        if(table.getLocalVariables(node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow()).stream().anyMatch(symbol -> symbol.getName().equals(node.get("name")))) {
            var id = node.get("name");
            Type type = TypeUtils.getExprType(node, table);
            String ollirType = OptUtils.toOllirType(type);

            code.append(id + ollirType);
        }
        else if(param){
            var id = node.get("name");
            Type type = TypeUtils.getExprType(node, table);
            String ollirType = OptUtils.toOllirType(type);

            code.append("$").append(pos).append(".").append(id).append(ollirType);
        }
        else{
            Type type = TypeUtils.getExprType(node, table);
            String ollirType = OptUtils.toOllirType(type);

            code.append(OptUtils.getTemp()).append(ollirType);
            computation.append(code).append(SPACE).append(ASSIGN).append(ollirType).append(SPACE);
            computation.append("getfield(this.").append(table.getClassName()).append(",");
            computation.append(node.get("name")).append(ollirType).append(")").append(ollirType).append(END_STMT);
        }
        return new OllirExprResult(code.toString() ,computation.toString());
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }
}
