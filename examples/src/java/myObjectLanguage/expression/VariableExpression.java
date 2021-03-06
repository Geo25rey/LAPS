package myObjectLanguage.expression;

import edu.rit.gec8773.laps.annotation.GrammarRule;
import myObjectLanguage.environment.Environment;
import myObjectLanguage.value.Value;

@GrammarRule
public class VariableExpression extends Expression {
    private String variableName;
    public VariableExpression(String var) {
        variableName = var;
    }

    @Override
    public Value evaluate(Environment environment) {
        return environment.get(variableName);
    }

    @Override
    public String toString() {
        return variableName;
    }
}
