import React, { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import BlocklyWorkspace from './components/BlocklyWorkspace';
import ResultPanel from './components/ResultPanel';
import { executeScript } from './api/procedures';

function App() {
    const [generatedCode, setGeneratedCode] = useState('// Код появится после добавления блоков');

    const mutation = useMutation({
        mutationFn: executeScript,
        onSuccess: (data) => console.log('Execute result:', data),
        onError: (err) => console.error('Execute error:', err),
    });

    const handleExecute = () => {
        if (!generatedCode || generatedCode.trim() === '' || generatedCode.includes('// Код появится')) {
            alert('Добавьте блоки на workspace');
            return;
        }
        mutation.mutate(generatedCode);
    };

    return (
        <div style={{ display: 'flex', height: '100vh' }}>
            <div style={{ flex: 2, padding: '10px', display: 'flex', flexDirection: 'column' }}>
                <h2>Blockly Constructor</h2>
                <div style={{ flex: 1, minHeight: 0 }}>
                    <BlocklyWorkspace onCodeChange={setGeneratedCode} />
                </div>
            </div>
            <div style={{ flex: 1, padding: '10px', borderLeft: '1px solid #ccc', display: 'flex', flexDirection: 'column' }}>
                <h2>Generated Code</h2>
                <pre style={{
                    background: '#f5f5f5',
                    padding: '10px',
                    overflow: 'auto',
                    flex: 1,
                    fontFamily: 'monospace',
                    fontSize: '13px',
                }}>
                    {generatedCode}
                </pre>
                <button
                    onClick={handleExecute}
                    disabled={mutation.isPending}
                    style={{
                        marginTop: '10px',
                        padding: '10px 20px',
                        background: mutation.isPending ? '#999' : '#4CAF50',
                        color: 'white',
                        border: 'none',
                        borderRadius: '4px',
                        cursor: mutation.isPending ? 'wait' : 'pointer',
                        fontSize: '14px',
                    }}
                >
                    {mutation.isPending ? '⏳ Executing...' : '▶ Execute'}
                </button>
            </div>
            <div style={{ flex: 1, padding: '10px', borderLeft: '1px solid #ccc', overflow: 'auto' }}>
                <h2>Result</h2>
                <ResultPanel
                    result={mutation.data}
                    isLoading={mutation.isPending}
                    isError={mutation.isError}
                    error={mutation.error}
                />
            </div>
        </div>
    );
}

export default App;