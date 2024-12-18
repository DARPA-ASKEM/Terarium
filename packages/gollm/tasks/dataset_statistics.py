import sys
import pandas as pd
import numpy as np
from entities import DatasetStatistics
from taskrunner import TaskRunnerInterface

def convert_numpy_types(obj):
    """
    Recursively convert NumPy types to standard Python types.
    """
    if isinstance(obj, np.integer):
        return int(obj)
    elif isinstance(obj, np.floating):
        return float(obj)
    elif isinstance(obj, np.ndarray):
        return obj.tolist()
    elif isinstance(obj, dict):
        return {k: convert_numpy_types(v) for k, v in obj.items()}
    elif isinstance(obj, list):
        return [convert_numpy_types(v) for v in obj]
    return obj

def cleanup():
    pass

def main():
    global taskrunner
    exit_code = 0

    try:
        taskrunner = TaskRunnerInterface(description="Dataset Statistics CLI")
        taskrunner.on_cancellation(cleanup)

        input_dict = taskrunner.read_input_dict_with_timeout()
        csv_url = DatasetStatistics(**input_dict).datasetUrl
        taskrunner.log(f"Reading CSV from {csv_url}")

        taskrunner.log("Creating statistics from input")

        try:
            # Read CSV from string
            df = pd.read_csv(csv_url)

            taskrunner.log(f"Read {len(df)} rows and {len(df.columns)} columns")

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

            response = convert_numpy_types({
                'numeric_columns': stats_summary,
                'non_numeric_columns': non_numeric_summary,
                'total_rows': len(df),
                'total_columns': len(df.columns)
            })

        except Exception as e:
            return f"An error occurred: {str(e)}"

        taskrunner.log("Statistics from input created")
        taskrunner.log(response)

        taskrunner.write_output_dict_with_timeout({"response": response})

    except Exception as e:
        sys.stderr.write(f"Error: {str(e)}\n")
        sys.stderr.flush()
        exit_code = 1

    taskrunner.log("Shutting down")
    taskrunner.shutdown()
    sys.exit(exit_code)

if __name__ == "__main__":
    main()
