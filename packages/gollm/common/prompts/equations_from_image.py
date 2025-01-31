EQUATIONS_FROM_IMAGE_PROMPT = """
This image will contain one or more equations. For each equation, create a LaTeX representation of the equation.
Multiple equations will probably be vertically separated, they may be enumerated by numbers or letters. Do not include these enumerations in the LaTeX equation.

Do not respond in full sentences; only create a JSON object that satisfies the JSON schema specified in the response format.

LaTeX equations need to conform to a standard form. Use the following guidelines to ensure that your LaTeX equations are correctly formatted:

--- STYLE GUIDE ---

{style_guide}

--- STYLE GUIDE END ---
"""
