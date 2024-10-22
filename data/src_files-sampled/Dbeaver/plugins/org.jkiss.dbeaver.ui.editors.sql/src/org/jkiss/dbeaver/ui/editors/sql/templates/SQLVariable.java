package org.jkiss.dbeaver.ui.editors.sql.templates;

import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.text.Position;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.templates.TemplateVariable;
import org.eclipse.jface.text.templates.TemplateVariableResolver;
import org.eclipse.jface.text.templates.TemplateVariableType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;


public class SQLVariable extends TemplateVariable {
    private static final Object DEFAULT_KEY = new Object();

    private SQLContext context;
    private TemplateVariableResolver resolver;
    private final Map<Object, Object[]> fValueMap = new HashMap<>();
    private Object fKey;
    private Object fCurrentChoice;

    public SQLVariable(SQLContext context, TemplateVariableType type, String name, int[] offsets)
    {
        super(type, name, name, offsets);
        this.context = context;
        fKey = DEFAULT_KEY;
        fValueMap.put(fKey, new String[]{name});
        fCurrentChoice = getChoices()[0];
    }

    public void setChoices(Object key, Object[] values)
    {
        Assert.isNotNull(key);
        Assert.isTrue(values.length > 0);
        if (fValueMap != null) {
            fValueMap.put(key, values);
            if (key.equals(fKey))
                fCurrentChoice = getChoices()[0];
            setResolved(true);
        }
    }

    public void setKey(Object defaultKey)
    {
        Assert.isTrue(fValueMap.containsKey(defaultKey));
        if (!fKey.equals(defaultKey)) {
            fKey = defaultKey;
            fCurrentChoice = getChoices()[0];
        }
    }

    public Object getCurrentChoice()
    {
        return fCurrentChoice;
    }

    public void setCurrentChoice(Object currentChoice)
    {
        Assert.isTrue(Arrays.asList(getChoices()).contains(currentChoice));
        fCurrentChoice = currentChoice;
    }

    @Override
    public void setValues(String[] values)
    {
        setChoices(values);
    }

    public void setChoices(Object[] values)
    {
        setChoices(DEFAULT_KEY, values);
    }

    @Override
    public String getDefaultValue()
    {
        return toString(fCurrentChoice);
    }

    public String toString(Object object)
    {
        return object.toString();
    }

    @Override
    public String[] getValues()
    {
        Object[] values = getChoices();
        String[] result = new String[values.length];
        for (int i = 0; i < result.length; i++)
            result[i] = toString(values[i]);
        return result;
    }

    public Object[] getChoices()
    {
        return getChoices(fKey);
    }

    public Object[] getChoices(Object key)
    {
        return fValueMap.get(key);
    }

    public Object[][] getAllChoices()
    {
        return fValueMap.values().toArray(new Object[fValueMap.size()][]);
    }

    public TemplateVariableResolver getResolver()
    {
        return resolver;
    }

    public void setResolver(TemplateVariableResolver resolver)
    {
        this.resolver = resolver;
    }

    public ICompletionProposal[] getProposals(Position position, int length)
    {
        if (resolver != null) {
            resolver.resolve(this, context);
        }
        String[] values = getValues();
        ICompletionProposal[] proposals = new ICompletionProposal[values.length];
        for (int j = 0; j < values.length; j++) {
            proposals[j] = new SQLVariableCompletionProposal(this, values[j], position, length);
        }
        return proposals;
    }

    public SQLContext getContext()
    {
        return context;
    }
}
