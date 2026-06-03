const ResultPanel = ({result, isLoading, isError, error}) => {
    if (isLoading) {
        return <div style={{padding: '20px', color: '#666'}}>⏳ Executing...</div>
    }

    if (isError) {
        const msg = error?.response?.data?.errorMessage || error?.message || 'Unknown error'
        return <div style={{padding: '20px', color: '#c00'}}>❌ Error: {msg}</div>
    }

    if (!result) {
        return <div style={{padding: '20px', color: '#999'}}>Нажмите "Execute" для запуска</div>
    }

    return (
        <div style={{padding: '10px'}}>
            <div style={{marginBottom: '10px'}}>
                <strong>Success:</strong> {result.success ? '✅' : '❌'}
            </div>
            {result.result !== undefined && result.result !== null && (
                <pre style={{
                    background: '#f5f5f5',
                    padding: '10px',
                    overflow: 'auto',
                    fontFamily: 'monospace',
                    fontSize: '13px',
                    maxHeight: '70vh',
                }}>
    {typeof result.result === 'string' ? result.result : JSON.stringify(result.result, null, 2)}
  </pre>
            )}
            {result.errorMessage && (
                <div style={{color: '#c00'}}>Error: {result.errorMessage}</div>
            )}
        </div>
    )
}

export default ResultPanel