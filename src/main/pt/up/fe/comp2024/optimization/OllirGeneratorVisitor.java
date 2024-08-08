package pt.up.fe.comp2024.optimization;

import org.fusesource.jansi.AnsiRenderer;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2024.ast.NodeUtils;
import pt.up.fe.comp2024.ast.TypeUtils;

import java.util.List;
import java.util.Spliterator;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final OllirExprGeneratorVisitor exprVisitor;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        exprVisitor = new OllirExprGeneratorVisitor(table);
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(IMPORT_DECL, this::visitImport);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(VAR_DECL, this::visitVar);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(EXPR_STMT, this::visitExpr);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(IF_STMT, this::visitIfStmt);

        setDefaultVisit(this::defaultVisit);
    }

    private String visitIfStmt(JmmNode jmmNode, Void unused) {

        StringBuilder code = new StringBuilder();

        JmmNode cond = jmmNode.getJmmChild(0);
        JmmNode ifBody = jmmNode.getJmmChild(1);
        JmmNode elseBody = jmmNode.getJmmChild(2);

        int temp = OptUtils.getNextTempNum();
        String ifStmt = "if_" + temp;
        String endStmt = "end_" + temp;

        code.append(exprVisitor.visit(cond).getComputation());
        code.append("if").append("(").append(exprVisitor.visit(cond).getCode()).append(")").append(" goto ").append(ifStmt).append(END_STMT);

        for(var node: ifBody.getChildren()){
            code.append(visit(node));
        }
        code.append("goto ").append(endStmt).append(END_STMT);
        code.append(ifStmt).append(":\n");

        for(var node: ifBody.getChildren()){
            code.append(visit(node));
        }
        code.append(endStmt).append(":\n");

        return code.toString();
    }

    private String visitWhileStmt(JmmNode jmmNode, Void unused) {
        StringBuilder code = new StringBuilder();

        var head = jmmNode.getJmmChild(0);
        var body = jmmNode.getJmmChild(1);

        int temp = OptUtils.getNextTempNum();
        String bodyWhile = "body_While" + temp;
        String endWhile = "end_While" + temp;

        code.append(exprVisitor.visit(head).getComputation());

        code.append("if").append("(").append(exprVisitor.visit(head).getCode()).append(")").append("goto ").append(bodyWhile).append(END_STMT);
        code.append("goto").append(SPACE).append(endWhile).append(END_STMT);
        code.append(bodyWhile).append(":\n");

        for(var node: body.getChildren()){
            code.append(visit(node));
        }

        code.append("if").append("(").append(exprVisitor.visit(head).getCode()).append(")").append("goto ").append(bodyWhile).append(END_STMT);
        code.append(endWhile).append(":\n");

        return code.toString();
    }

    private String visitExpr(JmmNode jmmNode, Void unused) {
        OllirExprResult expr = exprVisitor.visit(jmmNode.getJmmChild(0));
        return expr.getComputation();
    }

    private String visitImport(JmmNode jmmNode, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append("import ");

        var importName = jmmNode.get("name").replaceAll("[\\[\\]]", "").replace(", ", ".");
        code.append(importName);

        code.append(END_STMT);

        return code.toString();
    }

    private String visitVar(JmmNode jmmNode, Void unused) {

        StringBuilder code = new StringBuilder();

        if(jmmNode.getParent().getKind().equals(CLASS_DECL.toString())){
            code.append(".field public ");
        }
        code.append(jmmNode.get("name"));
        code.append(OptUtils.toOllirType(jmmNode.getJmmChild(0)));
        code.append(END_STMT);

        return code.toString();
    }

    private String visitAssignStmt(JmmNode node, Void unused) {

        var lhs = exprVisitor.visit(node.getJmmChild(0));
        var rhs = exprVisitor.visit(node.getJmmChild(1));

        StringBuilder code = new StringBuilder();

        // code to compute the children
        code.append(lhs.getComputation());
        code.append(rhs.getComputation());

        // code to compute self
        // statement has type of lhs
        Type thisType = TypeUtils.getExprType(node.getJmmChild(0), table);
        String typeString = OptUtils.toOllirType(thisType);


        code.append(lhs.getCode());
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        //code.append(" putfield(this.").append(table.getClassName()).append(",").append(node.getJmmChild(0).get("name")).append(OptUtils.toOllirType(TypeUtils.getExprType(node.getJmmChild(0), table))).append(",").append(rhs.getCode()).append(")").append(".V");

        code.append(END_STMT);

        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {

        String methodName = node.getAncestor(METHOD_DECL).map(method -> method.get("name")).orElseThrow();
        Type retType = table.getReturnType(methodName);

        StringBuilder code = new StringBuilder();

        var expr = OllirExprResult.EMPTY;

        if (node.getNumChildren() > 0) {
            expr = exprVisitor.visit(node.getJmmChild(0));
        }

        code.append(expr.getComputation());
        code.append("ret");
        code.append(OptUtils.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitParam(JmmNode node, Void unused) {

        var typeCode = OptUtils.toOllirType(node.getJmmChild(0));
        var id = node.get("name");

        String code = id + typeCode;

        return code;
    }


    private String visitMethodDecl(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        // name
        var count = 0;
        if(node.get("name").equals("main")){
            code.append("static main(args.array.String)");
        }
        else{
        var name = node.get("name");
        code.append(name);

        // param
        List<JmmNode> param = node.getChildren("Param");
        code.append("(");
        for(int i =0; i < param.size(); i++){
            JmmNode p = param.get(i);
            code.append(visitParam(p, null));
            count++;
            if (i < param.size()-1){
                code.append(", ");
            }
        }
        code.append(")");
        }

        // type
        var retType = OptUtils.toOllirType(table.getReturnType(node.get("name")).getName());
        code.append(retType);
        code.append(L_BRACKET);

        // rest of its children stmts
        var numberArgument = node.getNumChildren();
        for (int i = count; i < numberArgument; i++) {
            var child = node.getJmmChild(i);
            if ((child.getKind().equals("ReturnStmt")) || (child.getKind().equals("AssignStmt")) || (child.getKind().equals("ExprStmt"))) {
                var childCode = visit(child);
                code.append(childCode);
            }
        }

        // void
        if(retType.equals(".V")){
            code.append("ret.V");
            code.append(END_STMT);
        }

        code.append(R_BRACKET);
        code.append(NL);

        return code.toString();
    }

    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(table.getClassName());

        if(node.hasAttribute("exName")){
            String extendedName = node.get("exName");
            code.append(" extends ").append(extendedName);
        }

        code.append(L_BRACKET);

        code.append(NL);
        var needNl = true;

        for (var child : node.getChildren()) {
            var result = visit(child);

            if (METHOD_DECL.check(child) && needNl) {
                code.append(NL);
                needNl = false;
            }

            code.append(result);
        }

        code.append(buildConstructor());
        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        return ".construct " + table.getClassName() + "().V {\n" +
                "invokespecial(this, \"<init>\").V;\n" +
                "}\n";
    }


    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream()
                .map(this::visit)
                .forEach(code::append);

        return code.toString();
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}
