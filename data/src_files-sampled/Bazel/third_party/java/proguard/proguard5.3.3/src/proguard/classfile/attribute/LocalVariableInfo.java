package proguard.classfile.attribute;

import proguard.classfile.*;
import proguard.classfile.visitor.ClassVisitor;

public class LocalVariableInfo implements VisitorAccepter, Comparable
{
    public int u2startPC;
    public int u2length;
    public int u2nameIndex;
    public int u2descriptorIndex;
    public int u2index;

    public Clazz referencedClass;

    public Object visitorInfo;


    public LocalVariableInfo()
    {
    }


    public LocalVariableInfo(int   u2startPC,
                             int   u2length,
                             int   u2nameIndex,
                             int   u2descriptorIndex,
                             int   u2index)
    {
        this.u2startPC         = u2startPC;
        this.u2length          = u2length;
        this.u2nameIndex       = u2nameIndex;
        this.u2descriptorIndex = u2descriptorIndex;
        this.u2index           = u2index;
    }


    public String getName(Clazz clazz)
    {
        return clazz.getString(u2nameIndex);
    }


    public String getDescriptor(Clazz clazz)
    {
        return clazz.getString(u2descriptorIndex);
    }


    public void referencedClassAccept(ClassVisitor classVisitor)
    {
        if (referencedClass != null)
        {
            referencedClass.accept(classVisitor);
        }
    }


    public Object getVisitorInfo()
    {
        return visitorInfo;
    }

    public void setVisitorInfo(Object visitorInfo)
    {
        this.visitorInfo = visitorInfo;
    }


    public int compareTo(Object object)
    {
        LocalVariableInfo other = (LocalVariableInfo)object;

        return
            this.u2startPC          < other.u2startPC          ? -1 : this.u2startPC          > other.u2startPC          ? 1 :
            this.u2index            < other.u2index            ? -1 : this.u2index            > other.u2index            ? 1 :
            this.u2length           < other.u2length           ? -1 : this.u2length           > other.u2length           ? 1 :
            this.u2descriptorIndex  < other.u2descriptorIndex  ? -1 : this.u2descriptorIndex  > other.u2descriptorIndex  ? 1 :
            this.u2nameIndex        < other.u2nameIndex        ? -1 : this.u2nameIndex        > other.u2nameIndex        ? 1 :
                                                                                                                           0;
    }
}
