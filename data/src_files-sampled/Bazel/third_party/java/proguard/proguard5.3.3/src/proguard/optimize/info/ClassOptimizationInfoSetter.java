package proguard.optimize.info;

import proguard.classfile.ProgramClass;
import proguard.classfile.util.SimplifiedVisitor;
import proguard.classfile.visitor.ClassVisitor;
import proguard.optimize.KeepMarker;

public class ClassOptimizationInfoSetter
extends      SimplifiedVisitor
implements   ClassVisitor
{
    public void visitProgramClass(ProgramClass programClass)
    {
        if (!KeepMarker.isKept(programClass))
        {
            ClassOptimizationInfo.setClassOptimizationInfo(programClass);
        }
    }
}