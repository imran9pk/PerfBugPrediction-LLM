package edu.harvard.iq.dataverse.actionlogging;

import java.util.Date;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Stateless
public class ActionLogServiceBean {
    
    @PersistenceContext(unitName = "VDCNet-ejbPU")
    private EntityManager em;
    
    @TransactionAttribute(TransactionAttributeType.REQUIRES_NEW)
    public void log( ActionLogRecord rec ) {
        if ( rec.getEndTime() == null ) {
            rec.setEndTime( new Date() );
        }
        if ( rec.getActionResult() == null 
                && rec.getActionType() != ActionLogRecord.ActionType.Command ) {
            rec.setActionResult(ActionLogRecord.Result.OK);
        }
        em.persist(rec);
    }

    public void changeUserIdentifierInHistory(String oldIdentifier, String newIdentifier) {
        em.createNativeQuery(
                "UPDATE actionlogrecord "
                        + "SET useridentifier='"+newIdentifier+"', "
                        + "info='orig from "+oldIdentifier+" | ' || info "
                        + "WHERE useridentifier='"+oldIdentifier+"'"
        ).executeUpdate();
    }
   

    
}
