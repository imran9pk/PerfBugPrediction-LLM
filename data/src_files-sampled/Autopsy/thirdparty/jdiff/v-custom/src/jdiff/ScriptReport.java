package jdiff;

import java.util.*;

public class ScriptReport {

    public ScriptReport() { }

    public int run(APIComparator comp) {
        APIDiff apiDiff = comp.apiDiff;

        if(apiDiff.packagesRemoved.size() > 0) {
            return 102;
        }

        Iterator piter = apiDiff.packagesChanged.iterator();
        while (piter.hasNext()) {
            PackageDiff pkgDiff = (PackageDiff)(piter.next());
            if(pkgDiff.classesRemoved.size() > 0) {
                return 102;
            }

            Iterator citer = pkgDiff.classesChanged.iterator();
            while(citer.hasNext()) {
                ClassDiff classDiff = (ClassDiff)(citer.next());
                if(classDiff.methodsRemoved.size() > 0) {
                    return 102;
                }

                Iterator miter = classDiff.methodsChanged.iterator();
                while (miter.hasNext()) {
                    MemberDiff memberDiff = (MemberDiff)(miter.next());
                    if(!memberDiff.oldType_ .equals(memberDiff.newType_)) {
                        return 102;
                    }
                }
            }
        }
        
        if(apiDiff.packagesChanged.size() > 0) {
            return 101;
        }
        return 100;
    }

}
