package pt.up.fe.comp2024.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.tree.TreeNode;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;
import pt.up.fe.comp2024.backend.WordNumberAssociation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;



/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */



public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;

    List<Report> reports;

    String code;

    Method currentMethod;
    ClassUnit currentClass;
    private final FunctionClassMap<TreeNode, String> generators;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;

        reports = new ArrayList<>();
        code = null;
        currentMethod = null;

        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCondInst);
        generators.put(GotoInstruction.class, this::generateGotoInstruction);
        generators.put(OpCondInstruction.class, this::generateOpCondInst);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOpInst);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {

        // This way, build is idempotent
        if (code == null) {
            code = generators.apply(ollirResult.getOllirClass());
        }

        return code;
    }


    private String generateClassUnit(ClassUnit classUnit) {

        this.currentClass = classUnit;
        var code = new StringBuilder();

        // generate class name
        var className = classUnit.getClassName();
        var classAccessModifier = classUnit.getClassAccessModifier().toString().toLowerCase();
        code.append(".class ");
        if (!classAccessModifier.equals("default")) {
            code.append(classAccessModifier).append(" ");
        }
        var isClassStatic = classUnit.isStaticClass();
        var isClassFinal = classUnit.isFinalClass();
        if (isClassStatic) {
            code.append("static ");
        }
        if (isClassFinal) {
            code.append("final ");
        }
        code.append(className).append(NL);

        //super class

        var superClass = classUnit.getSuperClass();
        if (superClass != null) {
            code.append(".super ").append(superClass).append(NL);
        } else {
            superClass = "java/lang/Object";
            code.append(".super ").append(superClass).append(NL);
        }


        // fields

        for (var field : classUnit.getFields()){
            var fieldName = field.getFieldName();


            var fieldType =  getJasminTypeOfElement(field.getFieldType());

            var accessMod = field.getFieldAccessModifier();
            var initValue = field.getInitialValue();
            var isStatic = field.isStaticField();
            var isFinal = field.isFinalField();
            var isInitialized = field.isInitialized();
            code.append(".field ");
            if (accessMod != AccessModifier.DEFAULT) {
                code.append(accessMod.name().toLowerCase()).append(" ");
            }
            if (isStatic) {
                code.append("static ");
            }
            if (isFinal) {
                code.append("final ");
            }
            code.append(fieldName).append(" ").append(fieldType);
            if (isInitialized) {
                code.append(" = ").append(initValue).append(NL);
            } else {
                code.append(NL);
            }
        }


        String defaultConstructor = ".method public <init>()V" + NL +
                TAB + "aload_0" + NL +
                TAB + "invokespecial " + superClass + "/<init>()V" + NL +
                TAB + "return" + NL +
                ".end method" + NL;

        code.append(defaultConstructor);

        // generate code for all other methods
        for (var method : ollirResult.getOllirClass().getMethods()) {

            // Ignore constructor, since there is always one constructor
            // that receives no arguments, and has been already added
            // previously
            if (method.isConstructMethod()) {
                continue;
            }

            code.append(generators.apply(method));
        }

        return code.toString();
    }


    private String generateMethod(Method method) {

        // set method
        currentMethod = method;

        var code = new StringBuilder();
        var tempcode = new StringBuilder();
        var stackSize = 0;
        // calculate modifier
        var modifier = method.getMethodAccessModifier() != AccessModifier.DEFAULT ?
                method.getMethodAccessModifier().name().toLowerCase() + " " :
                "";

        var methodName = method.getMethodName();
        var returnType = getJasminTypeOfElement(method.getReturnType());

        var params = method.getParams().stream()
                .map(param -> getJasminTypeOfElement(param.getType()))
                .collect(Collectors.joining());

        // TODO: Hardcoded param types and return type, needs to be expanded !!!!DONE!!!!
        code.append("\n.method ").append(modifier);
        if(method.isStaticMethod()){
            code.append("static ");
        }
        if (method.isFinalMethod()) {
            code.append("final ");
        }
        code.append(methodName).append("(").append(params).append(")").append(returnType).append(NL);

        for (var inst : method.getInstructions()) {
            for(String label : method.getLabels(inst)) {
                tempcode.append(TAB + label).append(":\n");
            }
            var lines = StringLines.getLines(generators.apply(inst));
            for (var line : lines) {
                if (line.startsWith("ifne")) {
                    tempcode.append(line).append(NL);
                } else {
                    tempcode.append(TAB).append(line).append(NL);
                }
            }
        }
        var varTable = method.getVarTable();
        var maxLocals = 0;
        for (var descriptor : varTable.values()) {
            if (descriptor.getVirtualReg() > maxLocals) {
                maxLocals = descriptor.getVirtualReg();
            }
        }
        code.append(TAB).append(".limit locals ").append(maxLocals + 1).append(NL);
        stackSize = dealWithStack(tempcode);
        code.append(TAB).append(".limit stack ").append(stackSize).append(NL);
        code.append(tempcode);
        code.append(".end method\n");
        // unset method
        currentMethod = null;
        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        var code = new StringBuilder();
        // generate code for loading what's on the right
        if (assign.getRhs() instanceof CallInstruction) {
            CallInstruction call = (CallInstruction) assign.getRhs();
            if (call.getInvocationType() == CallType.NEW) {
                code.append(generateNewCall(call));
            } else {
                code.append(generators.apply(assign.getRhs()));
            }
        } else if (!(assign.getDest() instanceof ArrayOperand)) {
            code.append(generators.apply(assign.getRhs()));
        }

        // store value in the stack in destination
        var lhs = assign.getDest();

        if (!(lhs instanceof Operand)) {
            throw new NotImplementedException(lhs.getClass());
        }

        switch (lhs.getClass().toString()){
            case "class org.specs.comp.ollir.ArrayOperand" -> {
                var index = ((ArrayOperand) lhs).getIndexOperands().get(0);
                var reg = currentMethod.getVarTable().get(((ArrayOperand) lhs).getName()).getVirtualReg();
                code.append(dealLoadAssing("aload",reg) + NL);
                code.append(generators.apply(index));
                code.append(generators.apply(assign.getRhs()));
                code.append("iastore\n");

            }
            case "class org.specs.comp.ollir.Operand" -> {
                var operand = (Operand) lhs;
                var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
                switch (operand.getType().getTypeOfElement()){
                    case ARRAYREF, STRING, THIS, OBJECTREF -> code.append("astore ").append(reg).append(NL);

                    case BOOLEAN, INT32 -> code.append(dealLoadAssing("istore",reg)).append(NL);

                    default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
                }
            }
        }

        return code.toString();
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return generators.apply(singleOp.getSingleOperand());
    }
    private String generateSingleOpCondInst(SingleOpCondInstruction singleOpcCond){
        var code = new StringBuilder();
        var conditionSingleOp = singleOpcCond.getCondition().getSingleOperand();
        code.append(generators.apply(conditionSingleOp));
        code.append("ifne " + singleOpcCond.getLabel()).append("\n");
        return code.toString();
    }

    private String generateOpCondInst(OpCondInstruction opCond){
        var code = new StringBuilder();
        var condition = opCond.getCondition();
        code.append(generators.apply(condition));
        code.append(opCond.getLabel());
        return code.toString();
    }

    private String generateGotoInstruction(GotoInstruction gotoInst){
        return "goto " + gotoInst.getLabel() + "\n";
    }

    private String generateLiteral(LiteralElement literal) {
        Integer number = Integer.parseInt(literal.getLiteral());

        var code = new StringBuilder();
        if (number < 0) {
            code.append("iconst_m1");
        }
        else if (number < 6) {
            code.append("iconst_" + number);
        }
        else if (number < 128 && number > -129) {
            code.append("bipush " + number);
        }
        else if (number < 32768 && number > -32769) {
            code.append("sipush " + number);
        }
        else {
            code.append("ldc " + number);
        }
        return code.append(NL).toString();
    }

    private String generateOperand(Operand operand) {
        // get register
        if(operand instanceof ArrayOperand){
            var code = new StringBuilder();
            var index = ((ArrayOperand) operand).getIndexOperands().get(0);
            var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
            code.append(dealLoadAssing("aload",reg) + NL);
            code.append(generators.apply(index));
            code.append("iaload\n");
            return code.toString();
        } else {
            var reg = currentMethod.getVarTable().get(operand.getName()).getVirtualReg();
            return switch (operand.getType().getTypeOfElement()){
                case THIS -> "aload_0" + NL;
                case ARRAYREF, STRING, OBJECTREF -> dealLoadAssing("aload",reg) + NL;

                case BOOLEAN, INT32 -> dealLoadAssing("iload",reg) + NL;

                default -> throw new NotImplementedException(operand.getType().getTypeOfElement());
            };
        }

    }

    private String generateUnaryOpInst(UnaryOpInstruction unaryOp){
        var code = new StringBuilder();
        var operand = unaryOp.getOperand();
        code.append(generators.apply(operand));
        var op = switch (unaryOp.getOperation().getOpType()) {
            case NOTB -> "iconst_1" + NL + "ixor" + NL;
            default -> throw new NotImplementedException(unaryOp.getOperation().getOpType());
        };
        code.append(op).append(NL);
        return code.toString();

    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();

        // load values on the left and on the right
        code.append(generators.apply(binaryOp.getLeftOperand()));
        code.append(generators.apply(binaryOp.getRightOperand()));

        // apply operation
        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "iadd";
            case MUL -> "imul";
            case SUB -> "isub";
            case DIV -> "idiv";
            case LTH -> "if_icmplt ";
            case GTE -> "if_icmpge ";
            case EQ -> "if_icmpeq ";
            case LTE -> "if_icmple ";
            case GTH -> "if_icmpgt ";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
        };

        if(op.equals("if_icmplt " ) || op.equals("if_icmpge ") || op.equals("if_icmpeq ") || op.equals("if_icmple ") || op.equals("if_icmpgt ")){
            code.append(op);
        } else {
            code.append(op).append(NL);
        }

        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();

        // TODO: Hardcoded to int return type, needs to be expanded !!!DON'T FORGET!!!!!
        if (returnInst.getOperand() != null) {
            code.append(generators.apply(returnInst.getOperand()));

            if (returnInst.getOperand().getType().getTypeOfElement() == ElementType.INT32 || returnInst.getOperand().getType().getTypeOfElement() == ElementType.BOOLEAN) {
                code.append("ireturn").append(NL);
            } else {
                code.append("areturn").append(NL);
            }
        } else {
            code.append("return").append(NL);
        }
        return code.toString();
    }

    private String generatePutField(PutFieldInstruction putField){
        var code = new StringBuilder();
        var operand1 = putField.getOperands().get(0);
        var operand2 = putField.getOperands().get(1);
        var operand3 = putField.getOperands().get(2);

        code.append(generators.apply(operand1)).append(generators.apply(operand3));

        var className = getImportedClassName(operand1.getType().getTypeOfElement().toString());
        var name = ((Operand) operand2).getName();
        code.append("putfield ").append(className).append("/").append(name).append(" ").append(getJasminType(operand3.getType().toString())).append(NL);


        // generate code for loading what's on the right
        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getField){
        var code = new StringBuilder();
        var operand1 = getField.getOperands().get(0);
        var operand2 = getField.getOperands().get(1);

        code.append(generators.apply(operand1));

        var className = getImportedClassName(operand1.getType().getTypeOfElement().toString());
        var name = ((Operand) operand2).getName();
        code.append("getfield ").append(className).append("/").append(name).append(" ").append(getJasminType(operand2.getType().toString())).append(NL);

        return code.toString();
    }

    private String generateCall(CallInstruction call){

        var callType = call.getInvocationType();
        return switch (callType) {
            case invokespecial -> generateSpecialCall(call);

            case invokestatic -> generateStaticCall(call);
            case invokevirtual -> generateVirtualCall(call);
            case NEW -> generateNewCall(call);
            case arraylength -> callArray(call);
            default -> throw new NotImplementedException(callType);
        };



    }

    private String generateNewCall(CallInstruction call){
        if (call.getReturnType().getTypeOfElement() == ElementType.OBJECTREF){
            return this.callObj(call);
        } else {
            return this.callArray(call);
        }
    }

    private  String generateSpecialCall(CallInstruction call){
        var code = new StringBuilder();
        code.append(generators.apply(call.getOperands().get(0)));
        code.append("invokespecial ");
        var className = generateClassName(call);
        code.append(className);
        code.append("/<init>(");
        code.append(call.getArguments().stream().map(element -> getJasminTypeOfElement(element.getType())).collect(Collectors.joining()));
        code.append(")");
        code.append(getJasminTypeOfElement(call.getReturnType())).append(NL);
        return code.toString();
    }

    private String generateStaticCall(CallInstruction call){
        var code = new StringBuilder();
        var secondOperand = call.getOperands().get(1);

        for(Element element : call.getArguments()){
            code.append(generators.apply(element));
        }

        var className = generateClassName(call);
        code.append("invokestatic ").append(className).append("/").append(((LiteralElement) secondOperand).getLiteral().replaceAll("\"", ""));
        code.append("(");

        for (Element element : call.getArguments()){
            code.append(getJasminType(element.getType().toString()));
        }

        code.append(")");
        code.append(getJasminTypeOfElement(call.getReturnType())).append(NL);
        return code.toString();
    }

    private String generateVirtualCall(CallInstruction call){
        var code = new StringBuilder();
        var firstOperand = call.getOperands().get(0);
        code.append(generators.apply(firstOperand));
        var secondOperand = call.getOperands().get(1);
        for(Element element : call.getArguments()){
            code.append(generators.apply(element));
        }
        var className = generateClassName(call);
        code.append("invokevirtual ").append(className).append("/").append(((LiteralElement) secondOperand).getLiteral().replaceAll("\"", ""));
        code.append("(");
        for (Element element : call.getArguments()){
            code.append(getJasminTypeOfElement(element.getType()));
        }
        code.append(")");
        code.append(getJasminTypeOfElement(call.getReturnType())).append(NL);
        return code.toString();
    }

    private String generateClassName(CallInstruction call){
        var callType = call.getInvocationType();
        var firstOperand = call.getOperands().get(0);
        var className = "";
        switch (callType) {
            case invokevirtual, NEW -> {
                className = getImportedClassName(((ClassType) firstOperand.getType()).getName());
            }
            case invokestatic -> {
                className = getImportedClassName(((Operand) firstOperand).getName());
            }
            case invokespecial -> {
                ClassType elementType = (ClassType) firstOperand.getType();
                if (elementType.getTypeOfElement() == ElementType.THIS) {
                    className = this.currentClass.getSuperClass();
                } else {
                    className = getImportedClassName(elementType.getName());
                }
            }
            default -> throw new NotImplementedException(callType);
        }
        return className;
    }

    private String callObj(CallInstruction call){
        var code = new StringBuilder();
        var className = generateClassName(call);
        for (Element element : call.getArguments()){
            code.append(generators.apply(element));
        }
        code.append("new ");
        code.append(className).append(NL);
        return code.toString();

    }

    private String callArray(CallInstruction call){
        var code = new StringBuilder();
        for (Element element : call.getArguments()){
            code.append(generators.apply(element));
        }

        switch (call.getInvocationType()){
            case NEW ->{
                code.append("newarray int" + NL);
            }
            case arraylength -> {
                code.append(generators.apply(call.getCaller()));
                code.append("arraylength" + NL);
            }
            default -> throw new NotImplementedException(call.getInvocationType());
        }
        return code.toString();
    }
    private static final Map<String, String> TYPE_TO_JASMIN_MAP = Map.of(
            "INT32", "I",
            "FLOAT", "F",
            "DOUBLE", "D",
            "CHAR", "C",
            "BOOLEAN", "Z",
            "VOID", "V",
            "STRING", "Ljava/lang/String;"
    );

    private String getJasminType(String type){
        return TYPE_TO_JASMIN_MAP.getOrDefault(type, "L" + type + ";");
    }

    private String getJasminTypeOfElement(Type type){

        if (type.getTypeOfElement() == ElementType.ARRAYREF) {
            return "[" + getJasminType(type.toString().substring(0, type.toString().length() - 2));
        } else if (type.getTypeOfElement() == ElementType.OBJECTREF) {
            return "L" + getImportedClassName(((ClassType) type).getName()) + ";";
        } else {
            return getJasminType(type.toString());
        }
    }

    private String getImportedClassName(String className) {

        String result = "THIS".equals(className) ? this.currentClass.getClassName() : className;

        return this.currentClass.getImports().stream()
                .filter(importName -> importName.endsWith(result))
                .findFirst()
                .map(importName -> importName.replaceAll("\\.", "/"))
                .orElse(result);
    }

    private String dealLoadAssing(String instruction, Integer reg){
        if (reg < 4){
            return instruction + "_" + reg;
        } else {
            return instruction + " " + reg;
        }

    }

    private Integer dealWithStack(StringBuilder code){
        WordNumberAssociation wordNumberAssociation = new WordNumberAssociation();
        var stackSize = 0;
        var maxSize = 0;
        var lines = code.toString().split("\n");
        for (var line : lines){
            stackSize += wordNumberAssociation.getNumberFromLine(line);
            if (stackSize > maxSize){
                maxSize = stackSize;
            }

        }
        return maxSize;
    }
}


