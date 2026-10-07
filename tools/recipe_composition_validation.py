"""Whole recipe validation; prepared food is not its own ingredient recipe."""
import re


def identity_key(value):
    return re.sub(r'[\W_]+', '', value, flags=re.UNICODE)


def identity_keys(value):
    result = {identity_key(value)}
    qualified = re.fullmatch(r'([^()]+)\(([^(),]+)\)', value)
    if qualified:
        result.add(identity_key(qualified[2] + qualified[1]))
    return result


def composition_problem(reference):
    if reference['compositionKind'] != 'REFERENCE_RECIPE':
        return None
    inputs = reference['inputs']
    if len(inputs) == 1:
        if identity_keys(reference['name']) & identity_keys(inputs[0]['ingredient']):
            return 'SELF_COMPOSITE_WITHOUT_INGREDIENT_DECOMPOSITION'
    return reference.get('compositionValidationProblem')
