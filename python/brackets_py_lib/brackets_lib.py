
class BracketsNotBalancedException(Exception):
    def __init__(self, msg):
        self.message = msg

closed_brackets = {
    ")": "(",
    "]": "[",
    "}": "{",
}
open_brackets = {"(", "[", "{"}

def balanced_brackets(text):
    chars= list(text)
    stack = []
    for i, c in enumerate(chars):
        if c in open_brackets:
            stack.append(c)
        elif c in closed_brackets:
            if len(stack) == 0:
                raise BracketsNotBalancedException(f"closing bracket {c} with no opening bracket at char {i+1}")
            o = stack.pop()
            if o != closed_brackets[c]:
                raise BracketsNotBalancedException(f"closing bracket {c} at char {i+1} mismatched with last opening bracket {o}")
    if len(stack) > 0:
        raise BracketsNotBalancedException(f"opening brackets without closing brackets found: [{', '.join(stack)}]")
