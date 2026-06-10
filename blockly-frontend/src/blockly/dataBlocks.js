import * as Blockly from 'blockly'
import { javascriptGenerator } from 'blockly/javascript'

function hashString(str) {
    let hash = 0
    for (let i = 0; i < str.length; i++) {
        hash = str.charCodeAt(i) + ((hash << 5) - hash)
    }
    return Math.abs(hash) % 360
}

const OPERATORS = [
    ['= равно', '='],
    ['≠ не равно', '!='],
    ['> больше', '>'],
    ['< меньше', '<'],
    ['≥ больше или равно', '>='],
    ['≤ меньше или равно', '<='],
    ['like (ILIKE)', 'like'],
    ['~ regex', '~'],
    ['~* regex (регистр)', '~*'],
    ['!~ не regex', '!~'],
    ['!~* не regex (регистр)', '!~*'],
]

function registerConditionBlocks() {
    // data_condition: колонка + оператор + значение
    Blockly.Blocks['data_condition'] = {
        init: function () {
            this.appendValueInput('COLUMN')
                .setCheck('String')
                .appendField('колонка')
            this.appendDummyInput()
                .appendField(new Blockly.FieldDropdown(OPERATORS), 'OP')
            this.appendValueInput('VALUE')
            this.setOutput(true, 'Boolean')
            this.setColour(230)
            this.setTooltip('Условие: колонка оператор значение')
        }
    }
    javascriptGenerator.forBlock['data_condition'] = function (block, generator) {
        const col = generator.valueToCode(block, 'COLUMN', javascriptGenerator.ORDER_ATOMIC) || "''"
        const op = block.getFieldValue('OP')
        const val = generator.valueToCode(block, 'VALUE', javascriptGenerator.ORDER_ATOMIC) || "null"
        const code = `{${col}: {op: '${op}', value: ${val}}}`
        return [code, javascriptGenerator.ORDER_ATOMIC]
    }

    // data_condition_between: колонка BETWEEN x AND y
    Blockly.Blocks['data_condition_between'] = {
        init: function () {
            this.appendValueInput('COLUMN')
                .setCheck('String')
                .appendField('колонка')
            this.appendDummyInput()
                .appendField('между')
            this.appendValueInput('FROM')
            this.appendDummyInput()
                .appendField('и')
            this.appendValueInput('TO')
            this.setOutput(true, 'Boolean')
            this.setColour(230)
            this.setTooltip('Условие: колонка BETWEEN x AND y')
        }
    }
    javascriptGenerator.forBlock['data_condition_between'] = function (block, generator) {
        const col = generator.valueToCode(block, 'COLUMN', javascriptGenerator.ORDER_ATOMIC) || "''"
        const from = generator.valueToCode(block, 'FROM', javascriptGenerator.ORDER_ATOMIC) || "null"
        const to = generator.valueToCode(block, 'TO', javascriptGenerator.ORDER_ATOMIC) || "null"
        const code = `{${col}: {op: 'between', from: ${from}, to: ${to}}}`
        return [code, javascriptGenerator.ORDER_ATOMIC]
    }

    // data_condition_null: колонка IS NULL
    Blockly.Blocks['data_condition_null'] = {
        init: function () {
            this.appendValueInput('COLUMN')
                .setCheck('String')
                .appendField('колонка')
            this.appendDummyInput()
                .appendField('пустая (IS NULL)')
            this.setOutput(true, 'Boolean')
            this.setColour(230)
            this.setTooltip('Условие: колонка IS NULL')
        }
    }
    javascriptGenerator.forBlock['data_condition_null'] = function (block, generator) {
        const col = generator.valueToCode(block, 'COLUMN', javascriptGenerator.ORDER_ATOMIC) || "''"
        return [`{${col}: {op: 'is_null'}}`, javascriptGenerator.ORDER_ATOMIC]
    }

    // data_condition_not_null: колонка IS NOT NULL
    Blockly.Blocks['data_condition_not_null'] = {
        init: function () {
            this.appendValueInput('COLUMN')
                .setCheck('String')
                .appendField('колонка')
            this.appendDummyInput()
                .appendField('не пустая (IS NOT NULL)')
            this.setOutput(true, 'Boolean')
            this.setColour(230)
            this.setTooltip('Условие: колонка IS NOT NULL')
        }
    }
    javascriptGenerator.forBlock['data_condition_not_null'] = function (block, generator) {
        const col = generator.valueToCode(block, 'COLUMN', javascriptGenerator.ORDER_ATOMIC) || "''"
        return [`{${col}: {op: 'is_not_null'}}`, javascriptGenerator.ORDER_ATOMIC]
    }

    // data_condition_and: И
    Blockly.Blocks['data_condition_and'] = {
        init: function () {
            this.appendValueInput('A')
                .setCheck('Boolean')
                .appendField('И')
            this.appendValueInput('B')
                .appendField('и')
            this.setOutput(true, 'Boolean')
            this.setColour(210)
            this.setTooltip('Логическое И: оба условия должны быть истинны')
        }
    }
    javascriptGenerator.forBlock['data_condition_and'] = function (block, generator) {
        const a = generator.valueToCode(block, 'A', javascriptGenerator.ORDER_ATOMIC) || '{}'
        const b = generator.valueToCode(block, 'B', javascriptGenerator.ORDER_ATOMIC) || '{}'
        return [`{__and: [${a}, ${b}]}`, javascriptGenerator.ORDER_ATOMIC]
    }

    // data_condition_or: ИЛИ
    Blockly.Blocks['data_condition_or'] = {
        init: function () {
            this.appendValueInput('A')
                .setCheck('Boolean')
                .appendField('ИЛИ')
            this.appendValueInput('B')
                .appendField('или')
            this.setOutput(true, 'Boolean')
            this.setColour(210)
            this.setTooltip('Логическое ИЛИ: хотя бы одно условие должно быть истинным')
        }
    }
    javascriptGenerator.forBlock['data_condition_or'] = function (block, generator) {
        const a = generator.valueToCode(block, 'A', javascriptGenerator.ORDER_ATOMIC) || '{}'
        const b = generator.valueToCode(block, 'B', javascriptGenerator.ORDER_ATOMIC) || '{}'
        return [`{__or: [${a}, ${b}]}`, javascriptGenerator.ORDER_ATOMIC]
    }

    // data_condition_not: НЕ
    Blockly.Blocks['data_condition_not'] = {
        init: function () {
            this.appendValueInput('COND')
                .setCheck('Boolean')
                .appendField('НЕ')
            this.setOutput(true, 'Boolean')
            this.setColour(210)
            this.setTooltip('Логическое НЕ: инвертирует условие')
        }
    }
    javascriptGenerator.forBlock['data_condition_not'] = function (block, generator) {
        const cond = generator.valueToCode(block, 'COND', javascriptGenerator.ORDER_ATOMIC) || '{}'
        return [`{__not: ${cond}}`, javascriptGenerator.ORDER_ATOMIC]
    }

    // data_condition_in: значение в списке
    Blockly.Blocks['data_condition_in'] = {
        init: function () {
            this.appendValueInput('COLUMN')
                .setCheck('String')
                .appendField('колонка')
            this.appendDummyInput()
                .appendField('входит в список')
            this.appendValueInput('LIST')
                .setCheck('Array')
            this.setOutput(true, 'Boolean')
            this.setColour(230)
            this.setTooltip('Условие: колонка IN (список)')
        }
    }
    javascriptGenerator.forBlock['data_condition_in'] = function (block, generator) {
        const col = generator.valueToCode(block, 'COLUMN', javascriptGenerator.ORDER_ATOMIC) || "''"
        const list = generator.valueToCode(block, 'LIST', javascriptGenerator.ORDER_ATOMIC) || '[]'
        return [`{${col}: {op: 'in', value: ${list}}}`, javascriptGenerator.ORDER_ATOMIC]
    }

    // data_where_params: условия + лимит + смещение
    Blockly.Blocks['data_where_params'] = {
        init: function () {
            this.appendValueInput('COND')
                .setCheck('Boolean')
                .appendField('где')
            this.appendDummyInput()
                .appendField('лимит')
                .appendField(new Blockly.FieldNumber(100, 1, 1000, 1), 'LIMIT')
            this.appendDummyInput()
                .appendField('смещение')
                .appendField(new Blockly.FieldNumber(0, 0, 10000, 1), 'OFFSET')
            this.setOutput(true, 'Boolean')
            this.setColour(230)
            this.setTooltip('Условия с лимитом и смещением для пагинации')
        }
    }
    javascriptGenerator.forBlock['data_where_params'] = function (block, generator) {
        const cond = generator.valueToCode(block, 'COND', javascriptGenerator.ORDER_ATOMIC) || '{}'
        const limit = block.getFieldValue('LIMIT')
        const offset = block.getFieldValue('OFFSET')
        return [`{...(${cond}), __limit: ${limit}, __offset: ${offset}}`, javascriptGenerator.ORDER_ATOMIC]
    }

    // data_column: ввод имени колонки
    Blockly.Blocks['data_column'] = {
        init: function () {
            this.appendDummyInput()
                .appendField('колонка')
                .appendField(new Blockly.FieldTextInput('column_name'), 'NAME')
            this.setOutput(true, 'String')
            this.setColour(160)
            this.setTooltip('Имя колонки таблицы')
        }
    }
    javascriptGenerator.forBlock['data_column'] = function (block) {
        const name = block.getFieldValue('NAME')
        return [`'${name}'`, javascriptGenerator.ORDER_ATOMIC]
    }

    // data_value: ввод текстового значения
    Blockly.Blocks['data_value'] = {
        init: function () {
            this.appendDummyInput()
                .appendField('значение')
                .appendField(new Blockly.FieldTextInput(''), 'VALUE')
            this.setOutput(true, 'String')
            this.setColour(160)
            this.setTooltip('Текстовое значение')
        }
    }
    javascriptGenerator.forBlock['data_value'] = function (block) {
        const val = block.getFieldValue('VALUE')
        return [`'${val}'`, javascriptGenerator.ORDER_ATOMIC]
    }

    // === Блоки для работы с массивами ===

    // array_length: длина массива
    Blockly.Blocks['array_length'] = {
        init: function () {
            this.appendValueInput('ARRAY')
                .setCheck('Array')
                .appendField('длина массива')
            this.setOutput(true, 'Number')
            this.setColour(160)
            this.setTooltip('Количество элементов в массиве')
        }
    }
    javascriptGenerator.forBlock['array_length'] = function (block, generator) {
        const arr = generator.valueToCode(block, 'ARRAY', javascriptGenerator.ORDER_ATOMIC) || '[]'
        return [`${arr}.length`, javascriptGenerator.ORDER_ATOMIC]
    }

    // array_get: элемент по индексу
    Blockly.Blocks['array_get'] = {
        init: function () {
            this.appendValueInput('ARRAY')
                .setCheck('Array')
                .appendField('элемент')
            this.appendValueInput('INDEX')
                .setCheck('Number')
                .appendField('по индексу')
            this.setOutput(true)
            this.setColour(160)
            this.setTooltip('Получить элемент массива по индексу (начиная с 0)')
        }
    }
    javascriptGenerator.forBlock['array_get'] = function (block, generator) {
        const arr = generator.valueToCode(block, 'ARRAY', javascriptGenerator.ORDER_ATOMIC) || '[]'
        const idx = generator.valueToCode(block, 'INDEX', javascriptGenerator.ORDER_ATOMIC) || '0'
        return [`${arr}[${idx}]`, javascriptGenerator.ORDER_ATOMIC]
    }

    // array_forEach: цикл по массиву
    Blockly.Blocks['array_forEach'] = {
        init: function () {
            this.appendValueInput('ARRAY')
                .setCheck('Array')
                .appendField('для каждого элемента')
            this.appendDummyInput()
                .appendField('в переменной')
                .appendField(new Blockly.FieldTextInput('item'), 'VAR')
            this.appendStatementInput('DO')
                .appendField('выполнить')
            this.setPreviousStatement(true, null)
            this.setNextStatement(true, null)
            this.setColour(160)
            this.setTooltip('Выполнить действия для каждого элемента массива')
        }
    }
    javascriptGenerator.forBlock['array_forEach'] = function (block, generator) {
        const arr = generator.valueToCode(block, 'ARRAY', javascriptGenerator.ORDER_ATOMIC) || '[]'
        const varName = block.getFieldValue('VAR') || 'el'
        const body = generator.statementToCode(block, 'DO')
        return `for (var _idx = 0; _idx < ${arr}.length; _idx++) {\nvar ${varName} = ${arr}[_idx];\n${body}}\n`
    }

    // array_push: добавить в массив (создаёт новый)
    Blockly.Blocks['array_filter'] = {
        init: function () {
            this.appendValueInput('ARRAY')
                .setCheck('Array')
                .appendField('отфильтровать')
            this.appendValueInput('COND')
                .setCheck('Boolean')
                .appendField('где')
            this.setOutput(true, 'Array')
            this.setColour(160)
            this.setTooltip('Создать новый массив с элементами, удовлетворяющими условию')
        }
    }
    javascriptGenerator.forBlock['array_filter'] = function (block, generator) {
        const arr = generator.valueToCode(block, 'ARRAY', javascriptGenerator.ORDER_ATOMIC) || '[]'
        const cond = generator.valueToCode(block, 'COND', javascriptGenerator.ORDER_ATOMIC) || 'true'
        return [`${arr}.filter(function(item){ return ${cond}; })`, javascriptGenerator.ORDER_ATOMIC]
    }

    // array_map: преобразовать массив
    Blockly.Blocks['array_map'] = {
        init: function () {
            this.appendValueInput('ARRAY')
                .setCheck('Array')
                .appendField('преобразовать')
            this.appendValueInput('EXPR')
                .appendField('каждый в')
            this.setOutput(true, 'Array')
            this.setColour(160)
            this.setTooltip('Создать новый массив, преобразуя каждый элемент')
        }
    }
    javascriptGenerator.forBlock['array_map'] = function (block, generator) {
        const arr = generator.valueToCode(block, 'ARRAY', javascriptGenerator.ORDER_ATOMIC) || '[]'
        const expr = generator.valueToCode(block, 'EXPR', javascriptGenerator.ORDER_ATOMIC) || 'item'
        return [`${arr}.map(function(item){ return ${expr}; })`, javascriptGenerator.ORDER_ATOMIC]
    }

    // array_sum: сумма элементов
    Blockly.Blocks['array_sum'] = {
        init: function () {
            this.appendValueInput('ARRAY')
                .setCheck('Array')
                .appendField('сумма')
            this.appendDummyInput()
                .appendField('поле')
                .appendField(new Blockly.FieldTextInput('price'), 'FIELD')
            this.setOutput(true, 'Number')
            this.setColour(160)
            this.setTooltip('Сумма значений поля у всех элементов массива')
        }
    }
    javascriptGenerator.forBlock['array_sum'] = function (block, generator) {
        const arr = generator.valueToCode(block, 'ARRAY', javascriptGenerator.ORDER_ATOMIC) || '[]'
        const field = block.getFieldValue('FIELD') || 'price'
        return [`${arr}.reduce(function(s, item){ return s + (item.${field} || 0); }, 0)`, javascriptGenerator.ORDER_ATOMIC]
    }
}

function createTableCategories(tableData) {
    const subcategories = []

    for (const [tableName, columns] of Object.entries(tableData)) {
        const colour = hashString(tableName)
        const blockDefs = []
        const colNames = columns.map(c => c.column_name)

        // === Все записи из ===
        const selectAllType = `db_select_all_${tableName}`
        Blockly.Blocks[selectAllType] = {
            init: function () {
                this.appendDummyInput()
                    .appendField('все записи из')
                    .appendField(new Blockly.FieldDropdown([[tableName, tableName]]), 'TABLE')
                this.setOutput(true, 'Array')
                this.setColour(colour)
                this.setTooltip(`Все записи из таблицы ${tableName}`)
            }
        }
        javascriptGenerator.forBlock[selectAllType] = function () {
            return [`__toArr.toArray(DB.table('${tableName}').findAll())`, javascriptGenerator.ORDER_ATOMIC]
        }
        blockDefs.push({ kind: 'block', type: selectAllType })

        // === Все записи по условию ===
        const selectWhereType = `db_select_where_${tableName}`
        Blockly.Blocks[selectWhereType] = {
            init: function () {
                this.appendValueInput('COND')
                    .setCheck('Boolean')
                    .appendField('все записи из')
                    .appendField(new Blockly.FieldDropdown([[tableName, tableName]]), 'TABLE')
                    .appendField('где')
                this.setOutput(true, 'Array')
                this.setColour(colour)
                this.setTooltip(`Все записи из ${tableName} по условию`)
            }
        }
        javascriptGenerator.forBlock[selectWhereType] = function (block, generator) {
            const cond = generator.valueToCode(block, 'COND', javascriptGenerator.ORDER_ATOMIC) || '{}'
            return [`__toArr.toArray(DB.table('${tableName}').where(${cond}))`, javascriptGenerator.ORDER_ATOMIC]
        }
        blockDefs.push({ kind: 'block', type: selectWhereType })

        // === Найденная запись (по id) ===
        const findOneType = `db_find_one_${tableName}`
        Blockly.Blocks[findOneType] = {
            init: function () {
                this.appendValueInput('ID')
                    .setCheck('Number')
                    .appendField('найденная запись из')
                    .appendField(new Blockly.FieldDropdown([[tableName, tableName]]), 'TABLE')
                    .appendField('id')
                this.setOutput(true, 'Object')
                this.setColour(colour)
                this.setTooltip(`Найти запись по id в ${tableName}`)
            }
        }
        javascriptGenerator.forBlock[findOneType] = function (block, generator) {
            const id = generator.valueToCode(block, 'ID', javascriptGenerator.ORDER_ATOMIC) || '0'
            return [`DB.table('${tableName}').findById(${id})`, javascriptGenerator.ORDER_ATOMIC]
        }
        blockDefs.push({ kind: 'block', type: findOneType })

        // === Найденная запись по условию ===
        const findOneWhereType = `db_find_one_where_${tableName}`
        Blockly.Blocks[findOneWhereType] = {
            init: function () {
                this.appendValueInput('COND')
                    .setCheck('Boolean')
                    .appendField('найденная запись из')
                    .appendField(new Blockly.FieldDropdown([[tableName, tableName]]), 'TABLE')
                    .appendField('где')
                this.setOutput(true, 'Object')
                this.setColour(colour)
                this.setTooltip(`Найти одну запись по условию в ${tableName}`)
            }
        }
        javascriptGenerator.forBlock[findOneWhereType] = function (block, generator) {
            const cond = generator.valueToCode(block, 'COND', javascriptGenerator.ORDER_ATOMIC) || '{}'
            return [`DB.table('${tableName}').findOne(${cond})`, javascriptGenerator.ORDER_ATOMIC]
        }
        blockDefs.push({ kind: 'block', type: findOneWhereType })

        // === Получить поле из записи ===
        const getField = `db_get_field_${tableName}`
        Blockly.Blocks[getField] = {
            init: function () {
                this.appendValueInput('RECORD')
                    .appendField('из записи')
                this.appendDummyInput()
                    .appendField('поле')
                    .appendField(new Blockly.FieldDropdown(columns.map(c => [c.column_name, c.column_name])), 'FIELD')
                this.setOutput(true)
                this.setColour(colour)
                this.setTooltip(`Получить значение поля из записи ${tableName}`)
            }
        }
        javascriptGenerator.forBlock[getField] = function (block, generator) {
            const rec = generator.valueToCode(block, 'RECORD', javascriptGenerator.ORDER_ATOMIC) || '{}'
            const field = block.getFieldValue('FIELD')
            return [`${rec}.${field}`, javascriptGenerator.ORDER_ATOMIC]
        }
        blockDefs.push({ kind: 'block', type: getField })

        // === Создать запись ===
        const createType = `db_create_${tableName}`
        Blockly.Blocks[createType] = {
            init: function () {
                this.appendValueInput('DATA')
                    .appendField('создать запись в')
                    .appendField(new Blockly.FieldDropdown([[tableName, tableName]]), 'TABLE')
                this.setPreviousStatement(true, null)
                this.setNextStatement(true, null)
                this.setColour(colour)
                this.setTooltip(`Создать запись в ${tableName}`)
            }
        }
        javascriptGenerator.forBlock[createType] = function (block, generator) {
            const data = generator.valueToCode(block, 'DATA', javascriptGenerator.ORDER_ATOMIC) || '{}'
            return `DB.table('${tableName}').create(${data});\n`
        }
        blockDefs.push({ kind: 'block', type: createType })

        // === Обновить запись ===
        const updateType = `db_update_${tableName}`
        Blockly.Blocks[updateType] = {
            init: function () {
                this.appendValueInput('ID')
                    .setCheck('Number')
                    .appendField('обновить запись id')
                this.appendValueInput('DATA')
                    .appendField('данные')
                this.appendDummyInput()
                    .appendField('в')
                    .appendField(new Blockly.FieldDropdown([[tableName, tableName]]), 'TABLE')
                this.setPreviousStatement(true, null)
                this.setNextStatement(true, null)
                this.setColour(colour)
                this.setTooltip(`Обновить запись в ${tableName}`)
            }
        }
        javascriptGenerator.forBlock[updateType] = function (block, generator) {
            const id = generator.valueToCode(block, 'ID', javascriptGenerator.ORDER_ATOMIC) || '0'
            const data = generator.valueToCode(block, 'DATA', javascriptGenerator.ORDER_ATOMIC) || '{}'
            return `DB.table('${tableName}').update(${id}, ${data});\n`
        }
        blockDefs.push({ kind: 'block', type: updateType })

        // === Удалить запись ===
        const deleteType = `db_delete_${tableName}`
        Blockly.Blocks[deleteType] = {
            init: function () {
                this.appendValueInput('ID')
                    .setCheck('Number')
                    .appendField('удалить запись id')
                this.appendDummyInput()
                    .appendField('из')
                    .appendField(new Blockly.FieldDropdown([[tableName, tableName]]), 'TABLE')
                this.setPreviousStatement(true, null)
                this.setNextStatement(true, null)
                this.setColour(colour)
                this.setTooltip(`Удалить запись из ${tableName}`)
            }
        }
        javascriptGenerator.forBlock[deleteType] = function (block, generator) {
            const id = generator.valueToCode(block, 'ID', javascriptGenerator.ORDER_ATOMIC) || '0'
            return `DB.table('${tableName}').delete(${id});\n`
        }
        blockDefs.push({ kind: 'block', type: deleteType })

        // === Колонка из таблицы (dropdown) ===
        const columnType = `db_column_${tableName}`
        const colDropdown = columns.map(c => [c.column_name, c.column_name])
        Blockly.Blocks[columnType] = {
            init: function () {
                this.appendDummyInput()
                    .appendField('колонка')
                    .appendField(new Blockly.FieldDropdown(colDropdown), 'COLUMN')
                    .appendField('из')
                    .appendField(new Blockly.FieldDropdown([[tableName, tableName]]), 'TABLE')
                this.setOutput(true, 'String')
                this.setColour(colour)
                this.setTooltip(`Имя колонки из таблицы ${tableName}`)
            }
        }
        javascriptGenerator.forBlock[columnType] = function (block) {
            const col = block.getFieldValue('COLUMN')
            return [`'${col}'`, javascriptGenerator.ORDER_ATOMIC]
        }
        blockDefs.push({ kind: 'block', type: columnType })

        subcategories.push({
            kind: 'category',
            name: tableName,
            colour,
            contents: blockDefs,
        })
    }

    return {
        kind: 'category',
        name: 'Данные',
        colour: '#0078D7',
        contents: [
            {
                kind: 'category',
                name: 'Условия',
                colour: 210,
                contents: [
                    { kind: 'block', type: 'data_condition' },
                    { kind: 'block', type: 'data_condition_between' },
                    { kind: 'block', type: 'data_condition_null' },
                    { kind: 'block', type: 'data_condition_not_null' },
                    { kind: 'block', type: 'data_condition_and' },
                    { kind: 'block', type: 'data_condition_or' },
                    { kind: 'block', type: 'data_condition_not' },
                    { kind: 'block', type: 'data_condition_in' },
                    { kind: 'block', type: 'data_where_params' },
                    { kind: 'block', type: 'data_column' },
                    { kind: 'block', type: 'data_value' },
                ],
            },
            ...subcategories,
        ],
    }
}

registerConditionBlocks()

export { createTableCategories }
