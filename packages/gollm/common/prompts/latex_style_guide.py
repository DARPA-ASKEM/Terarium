LATEX_STYLE_GUIDE = """
1) Derivatives must be written in Leibniz notation (for example, "\\frac{{d X}}{{d t}}")
    a) Derivatives that are written in other notations, like Newton ("\\dot{{X}}") or Lagrange ("X^\\prime" or "X'"), should be converted to Leibniz notation
    b) Partial derivatives of one-variable functions (for example, "\\partial_t X" or "\\frac{{\\partial X}}{{\\partial t}}") should be rewritten as ordinary derivatives ("\\frac{{d X}}{{d t}}")
2) First-order derivative must be on the left of the equal sign
3) All variables that have time "t" dependence should be written with an explicit "(t)" (for example, "X" should be written as "X(t)")
4) Rewrite superscripts and LaTeX superscripts "^" that denote indices to subscripts using LaTeX "_"
5) Replace any unicode subscripts with LaTeX subscripts using "_". Ensure that all characters used in the subscript are surrounded by a pair of curly brackets "{{...}}"
6) Use " * " to denote multiplication
    a) for example, replace "b S(t) I(t)" with "b * S(t) * I(t)"
    b) for example, replace "(a b)I(t) with "a * b * I(t)"
7) Avoid capital sigma and pi notations for summation and product
8) Avoid non-ASCII characters when possible
9) Avoid using homoglyphs
10) Avoid words or multi-character names for variables and names. Use camel case to express multi-word or multi-character names
11) Replace any variant form of Greek letters to their main form when representing a variable or parameter 
    a) for example, replace "\\varepsilon" with "\\epsilon"
    b) for example, replace "\\vartheta" with "\\theta"
    c) for example, replace "\\varpi" with "\\pi"
    d) for example, replace "\\varrho" with "\\rho"
    e) for example, replace "\\varsigma" with "\\sigma"
    f) for example, replace "\\varphi" with "\\phi"
12) If equations are separated by punctuation (like comma, period, semicolon), do not include the punctuation in the LaTeX code.
13) Rewrite expressions with negative exponents as explicit fractions
    a) for example, replace "N^{{-1}}" with "\\frac{{1}}{{N}}"
14) Do not use square brackets "[ ]", curly braces "{ }", and angle brackets "< >" when grouping expressions
15) Always expand expressions surrounded by parentheses using the order of mathematical operations
    a) for example, replace "x(t) (\\alpha y(t) + \\beta z(t))" with "\\alpha * x(t) * y(t) + \\beta * x(t) * z(t)"
    b) for example, replace "-(\\alpha + \\beta + \\gamma) x(t)" with "- \\alpha * x(t) - \\beta * x(t) - \\gamma * x(t)"
    c) for example, replace "(x(t) + y(t)) (z(t) + w(t))" with "x(t) * z(t) + x(t) * w(t) + y(t) * z(t) + y(t) * w(t)"
"""
