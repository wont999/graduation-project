import React, { useEffect, useRef } from 'react';
import * as Blockly from 'blockly';
import { javascriptGenerator } from 'blockly/javascript';

javascriptGenerator.forBlock['text_print'] = function(block, generator) {
    const text = generator.valueToCode(block, 'TEXT', javascriptGenerator.ORDER_NONE) || "''";
    return text + ';\n';
};

const BlocklyWorkspace = ({ onCodeChange }) => {
    const blocklyDiv = useRef(null);
    const workspaceRef = useRef(null);

    useEffect(() => {
        if (!blocklyDiv.current) return;

        blocklyDiv.current.innerHTML = '';

        workspaceRef.current = Blockly.inject(blocklyDiv.current, {
            toolbox: {
                kind: 'categoryToolbox',
                contents: [
                    {
                        kind: 'category',
                        name: 'Logic',
                        colour: '#5C81A6',
                        contents: [
                            { kind: 'block', type: 'controls_if' },
                            { kind: 'block', type: 'logic_compare' },
                            { kind: 'block', type: 'logic_operation' },
                            { kind: 'block', type: 'logic_boolean' },
                        ],
                    },
                    {
                        kind: 'category',
                        name: 'Math',
                        colour: '#5CA65C',
                        contents: [
                            { kind: 'block', type: 'math_number' },
                            { kind: 'block', type: 'math_arithmetic' },
                            { kind: 'block', type: 'math_round' },
                        ],
                    },
                    {
                        kind: 'category',
                        name: 'Text',
                        colour: '#A65C5C',
                        contents: [
                            { kind: 'block', type: 'text' },
                            { kind: 'block', type: 'text_print' },
                        ],
                    },
                    {
                        kind: 'category',
                        name: 'Variables',
                        colour: '#A65C81',
                        custom: 'VARIABLE',
                    },
                ],
            },
            grid: {
                spacing: 20,
                length: 3,
                colour: '#ccc',
                snap: true,
            },
            zoom: {
                controls: true,
                wheel: true,
                startScale: 1.0,
            },
        });

        const handleChange = () => {
            try {
                if (!workspaceRef.current) return;
                const code = javascriptGenerator.workspaceToCode(workspaceRef.current);
                console.log('Generated code:', code);
                console.log('Blocks count:', workspaceRef.current.getAllBlocks().length);
                onCodeChange(code);
            } catch (e) {
                console.error('Error in handleChange:', e);
            }
        };

        workspaceRef.current.addChangeListener(handleChange);

        return () => {
            if (workspaceRef.current) {
                workspaceRef.current.removeChangeListener(handleChange);
                workspaceRef.current.dispose();
                workspaceRef.current = null;
            }
        };
    }, [onCodeChange]);

    return <div ref={blocklyDiv} style={{ height: '100%', width: '100%' }} />;
};

export default BlocklyWorkspace;