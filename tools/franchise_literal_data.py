"""Restricted static object literals; never evaluates JavaScript or expressions."""
import ast, re

def parse_object_literal(source):
    tokens=[];position=0
    pattern=re.compile(r'''\s*(?:((?:"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'))|([A-Za-z_$][\w$]*)|([{}\[\]:,]))''')
    while position<len(source):
        if not source[position:].strip():break
        match=pattern.match(source,position)
        assert match,'Nonliteral JavaScript in official menu data'
        tokens.append(('string',ast.literal_eval(match[1])) if match[1] else ('key',match[2]) if match[2] else ('punct',match[3]))
        position=match.end()
    cursor=0
    def value(depth=0):
        nonlocal cursor
        assert depth<12 and cursor<len(tokens),'Invalid menu literal depth'
        kind,token=tokens[cursor];cursor+=1
        if kind=='string':return token
        assert token in ('{','['),'Only objects, arrays and strings are supported'
        result={} if token=='{' else [];end='}' if token=='{' else ']'
        while cursor<len(tokens) and tokens[cursor][1]!=end:
            if isinstance(result,dict):
                key_kind,key=tokens[cursor];cursor+=1
                assert key_kind in ('string','key') and key not in result
                assert tokens[cursor][1]==':';cursor+=1;result[key]=value(depth+1)
            else:result.append(value(depth+1))
            assert cursor<len(tokens)
            if tokens[cursor][1]!=',':break
            cursor+=1
        assert tokens[cursor][1]==end;cursor+=1
        return result
    result=value();assert cursor==len(tokens) and isinstance(result,dict)
    return result
