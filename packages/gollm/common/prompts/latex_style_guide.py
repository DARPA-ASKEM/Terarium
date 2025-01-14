LATEX_STYLE_GUIDE = """
1) Derivatives must be written in Leibniz notation (for example, "\\frac{{d X}}{{d t}}")
    a) Derivatives that are written in other notations, like Newton ("\\dot{{X}}") or Lagrange ("X^\\prime" or "X'"), should be converted to Leibniz notation
    b) Partial derivatives of one-variable functions (for example, "\\partial_t X" or "\\frac{{\\partial X}}{{\\partial t}}") should be rewritten as ordinary derivatives ("\\frac{{d X}}{{d t}}")
2) First-order derivative must be on the left of the equal sign
3) All variables that have time "t" dependence should be written with an explicit "(t)" (for example, "X" should be written as "X(t)")
4) Rewrite superscripts and LaTeX superscripts "^" that denote indices to subscripts using LaTeX "_"
5) Replace any unicode subscripts with LaTeX subscripts using "_". Ensure that all characters used in the subscript are surrounded by a pair of curly brackets "{{...}}"
6) Avoid parentheses
7) Avoid capital sigma and pi notations for summation and product
8) Avoid non-ASCII characters when possible
9) Avoid using homoglyphs
10) Avoid words or multi-character names for variables and names. Use camel case to express multi-word or multi-character names
11) Use " * " to denote multiplication between scalar quantities
12) Replace any variant form of Greek letters to their main form when representing a variable or parameter; "\\varepsilon" -> "\\epsilon", "\\vartheta" -> "\\theta", "\\varpi" -> "\\pi", "\\varrho" -> "\\rho",  "\\varsigma" -> "\\sigma", "\\varphi" -> "\\phi"
13) If equations are separated by punctuation (like comma, period, semicolon), do not include the punctuation in the LaTeX code.
14) Expand all algebraic expressions with parentheses according to distributivity property of multiplication (for example, "S(t) * (a * I(t) + b * D(t) + c * A(t))" -> "a * S(t) * I(t) + b * S(t) * D(t) + c * A(t) * S(t)")
"""
