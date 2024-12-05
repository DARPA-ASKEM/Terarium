import pandas as pd
import numpy as np

def analyze_csv(csv_string):
    """
    Analyze a CSV from a string input.

    Parameters:
    csv_string (str): CSV content as a string

    Returns:
    dict: Comprehensive statistical summary
    """
    try:
        # Read CSV from string
        df = pd.read_csv(io.StringIO(csv_string))

        # Prepare a dictionary to store results
        stats_summary = {}

        # Iterate through numeric columns
        for column in df.select_dtypes(include=[np.number]).columns:
            # Calculate basic statistics
            column_stats = {
                'data_type': str(df[column].dtype),
                'mean': df[column].mean(),
                'median': df[column].median(),
                'min': df[column].min(),
                'max': df[column].max(),
                'std_dev': df[column].std(),
                'quartiles': df[column].quantile([0.25, 0.5, 0.75]).tolist(),
                'unique_values': df[column].nunique(),
                'missing_values': df[column].isnull().sum(),
            }

            # Add distribution binning
            column_stats['histogram_bins'] = np.histogram(df[column].dropna(), bins='auto')[1].tolist()

            stats_summary[column] = column_stats

        # Non-numeric column analysis
        non_numeric_columns = df.select_dtypes(exclude=[np.number]).columns
        non_numeric_summary = {}
        for column in non_numeric_columns:
            non_numeric_summary[column] = {
                'data_type': str(df[column].dtype),
                'unique_values': df[column].nunique(),
                'most_common': df[column].value_counts().head(5).to_dict(),
                'missing_values': df[column].isnull().sum()
            }

        return {
            'numeric_columns': stats_summary,
            'non_numeric_columns': non_numeric_summary,
            'total_rows': len(df),
            'total_columns': len(df.columns)
        }

    except Exception as e:
        return f"An error occurred: {str(e)}"
