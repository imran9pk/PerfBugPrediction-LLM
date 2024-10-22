package proguard.optimize.info;

import proguard.classfile.*;
import proguard.classfile.attribute.CodeAttribute;
import proguard.classfile.instruction.*;
import proguard.classfile.instruction.visitor.InstructionVisitor;
import proguard.classfile.util.SimplifiedVisitor;

public class ExceptionInstructionChecker
extends      SimplifiedVisitor
implements   InstructionVisitor
{
    private boolean mayThrowExceptions;


    public boolean mayThrowExceptions(Clazz         clazz,
                                      Method        method,
                                      CodeAttribute codeAttribute)
    {
        return mayThrowExceptions(clazz,
                                  method,
                                  codeAttribute,
                                  0,
                                  codeAttribute.u4codeLength);
    }


    public boolean mayThrowExceptions(Clazz         clazz,
                                      Method        method,
                                      CodeAttribute codeAttribute,
                                      int           startOffset,
                                      int           endOffset)
    {
        byte[] code = codeAttribute.code;

        int offset = startOffset;
        while (offset < endOffset)
        {
            Instruction instruction = InstructionFactory.create(code, offset);

            if (mayThrowExceptions(clazz,
                                   method,
                                   codeAttribute,
                                   offset,
                                   instruction))
            {
                return true;
            }

            offset += instruction.length(offset);
        }

        return false;
    }


    public boolean mayThrowExceptions(Clazz         clazz,
                                      Method        method,
                                      CodeAttribute codeAttribute,
                                      int           offset)
    {
        Instruction instruction = InstructionFactory.create(codeAttribute.code, offset);

        return mayThrowExceptions(clazz,
                                  method,
                                  codeAttribute,
                                  offset,
                                  instruction);
    }


    public boolean mayThrowExceptions(Clazz         clazz,
                                      Method        method,
                                      CodeAttribute codeAttribute,
                                      int           offset,
                                      Instruction   instruction)
    {
        return instruction.mayThrowExceptions();

}


    public void visitAnyInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, Instruction instruction) {}


    public void visitSimpleInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, SimpleInstruction simpleInstruction)
    {
        switch (simpleInstruction.opcode)
        {
            case InstructionConstants.OP_IDIV:
            case InstructionConstants.OP_LDIV:
            case InstructionConstants.OP_IREM:
            case InstructionConstants.OP_LREM:
            case InstructionConstants.OP_IALOAD:
            case InstructionConstants.OP_LALOAD:
            case InstructionConstants.OP_FALOAD:
            case InstructionConstants.OP_DALOAD:
            case InstructionConstants.OP_AALOAD:
            case InstructionConstants.OP_BALOAD:
            case InstructionConstants.OP_CALOAD:
            case InstructionConstants.OP_SALOAD:
            case InstructionConstants.OP_IASTORE:
            case InstructionConstants.OP_LASTORE:
            case InstructionConstants.OP_FASTORE:
            case InstructionConstants.OP_DASTORE:
            case InstructionConstants.OP_AASTORE:
            case InstructionConstants.OP_BASTORE:
            case InstructionConstants.OP_CASTORE:
            case InstructionConstants.OP_SASTORE:
            case InstructionConstants.OP_NEWARRAY:
            case InstructionConstants.OP_ARRAYLENGTH:
            case InstructionConstants.OP_ATHROW:
            case InstructionConstants.OP_MONITORENTER:
                mayThrowExceptions = true;
        }
    }


    public void visitConstantInstruction(Clazz clazz, Method method, CodeAttribute codeAttribute, int offset, ConstantInstruction constantInstruction)
    {
        switch (constantInstruction.opcode)
        {
            case InstructionConstants.OP_GETSTATIC:
            case InstructionConstants.OP_PUTSTATIC:
            case InstructionConstants.OP_GETFIELD:
            case InstructionConstants.OP_PUTFIELD:
            case InstructionConstants.OP_INVOKEVIRTUAL:
            case InstructionConstants.OP_INVOKESPECIAL:
            case InstructionConstants.OP_INVOKESTATIC:
            case InstructionConstants.OP_INVOKEINTERFACE:
            case InstructionConstants.OP_INVOKEDYNAMIC:
            case InstructionConstants.OP_NEW:
            case InstructionConstants.OP_ANEWARRAY:
            case InstructionConstants.OP_CHECKCAST:
            case InstructionConstants.OP_INSTANCEOF:
            case InstructionConstants.OP_MULTIANEWARRAY:
                mayThrowExceptions = true;

}
    }



