import * as Blockly from 'blockly'
import { javascriptGenerator } from 'blockly/javascript'
import { FieldMultilineInput } from '@blockly/field-multilineinput'

export function initCustomBlocks() {
    Blockly.Blocks['logic_ternary'] = {
        init: function() {
            this.appendValueInput('CONDITION')
                .setCheck('Boolean')
                .appendField('если')
            this.appendValueInput('THEN')
                .appendField('то')
            this.appendValueInput('ELSE')
                .appendField('иначе')
            this.setOutput(true)
            this.setColour(230)
            this.setTooltip('Тернарная операция: если условие истинно, вернуть первое значение, иначе второе')
        }
    }

    javascriptGenerator.forBlock['logic_ternary'] = function(block, generator) {
        const condition = generator.valueToCode(block, 'CONDITION', javascriptGenerator.ORDER_CONDITIONAL) || 'false'
        const thenVal = generator.valueToCode(block, 'THEN', javascriptGenerator.ORDER_CONDITIONAL) || 'null'
        const elseVal = generator.valueToCode(block, 'ELSE', javascriptGenerator.ORDER_CONDITIONAL) || 'null'
        return [`${condition} ? ${thenVal} : ${elseVal}`, javascriptGenerator.ORDER_CONDITIONAL]
    }
}


Blockly.Blocks['text_limited'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('"')
            .appendField(new Blockly.FieldTextInput('', function(text) {
                if (text.length > 128) {
                    alert('Максимум 128 символов!')
                    return null
                }
                return text
            }), 'TEXT')
            .appendField('"')
        this.setOutput(true, 'String')
        this.setColour(160)
        this.setTooltip('Текстовый литерал (до 128 символов)')
    }
}

javascriptGenerator.forBlock['text_limited'] = function(block) {
    const text = block.getFieldValue('TEXT')
    return [`'${text}'`, javascriptGenerator.ORDER_ATOMIC]
}

Blockly.Blocks['text_multiline'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('многострочный')
            .appendField(new FieldMultilineInput('', null, {
                maxLength: 1024
            }), 'TEXT')
        this.setOutput(true, 'String')
        this.setColour(160)
        this.setTooltip('Многострочный текст (до 1024 символов)')
    }
}

javascriptGenerator.forBlock['text_multiline'] = function(block) {
    const text = block.getFieldValue('TEXT')
    const escaped = text.replace(/'/g, "\\'").replace(/\n/g, '\\n')
    return [`'${escaped}'`, javascriptGenerator.ORDER_ATOMIC]
}


// === ДАТЫ ===

// Текущая дата и время
Blockly.Blocks['date_now'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('текущая дата и время')
        this.setOutput(true, 'String')
        this.setColour(230)
        this.setTooltip('Возвращает текущую дату и время в формате ISO')
    }
}

javascriptGenerator.forBlock['date_now'] = function() {
    return ['new Date().toISOString()', javascriptGenerator.ORDER_ATOMIC]
}

// Только текущая дата
Blockly.Blocks['date_today'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('текущая дата')
        this.setOutput(true, 'String')
        this.setColour(230)
        this.setTooltip('Возвращает текущую дату в формате YYYY-MM-DD')
    }
}

javascriptGenerator.forBlock['date_today'] = function() {
    return ['new Date().toISOString().split("T")[0]', javascriptGenerator.ORDER_ATOMIC]
}

// Текущее время с выбором пояса
Blockly.Blocks['date_time_now'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('текущее время')
            .appendField(new Blockly.FieldDropdown([
                ['Москва (UTC+3)', 'Europe/Moscow'],
                ['UTC', 'UTC'],
                ['Нью-Йорк (UTC-5)', 'America/New_York'],
                ['Лондон (UTC+0)', 'Europe/London'],
                ['Токио (UTC+9)', 'Asia/Tokyo']
            ]), 'TIMEZONE')
        this.setOutput(true, 'String')
        this.setColour(230)
        this.setTooltip('Возвращает текущее время в указанном часовом поясе')
    }
}

javascriptGenerator.forBlock['date_time_now'] = function(block) {
    const tz = block.getFieldValue('TIMEZONE')
    return [`new Date().toLocaleTimeString('ru-RU', {timeZone: '${tz}'})`, javascriptGenerator.ORDER_ATOMIC]
}

// Создать дату и время
Blockly.Blocks['date_create'] = {
    init: function() {
        this.appendValueInput('YEAR').setCheck('Number').appendField('дата')
        this.appendValueInput('MONTH').setCheck('Number').appendField('-')
        this.appendValueInput('DAY').setCheck('Number').appendField('-')
        this.appendValueInput('HOUR').setCheck('Number').appendField('час')
        this.appendValueInput('MINUTE').setCheck('Number').appendField(':')
        this.appendValueInput('SECOND').setCheck('Number').appendField(':')
        this.setOutput(true, 'String')
        this.setColour(230)
        this.setTooltip('Создать дату и время из компонентов')
    }
}

javascriptGenerator.forBlock['date_create'] = function(block, generator) {
    const year = generator.valueToCode(block, 'YEAR', javascriptGenerator.ORDER_ATOMIC) || '2024'
    const month = generator.valueToCode(block, 'MONTH', javascriptGenerator.ORDER_ATOMIC) || '1'
    const day = generator.valueToCode(block, 'DAY', javascriptGenerator.ORDER_ATOMIC) || '1'
    const hour = generator.valueToCode(block, 'HOUR', javascriptGenerator.ORDER_ATOMIC) || '0'
    const minute = generator.valueToCode(block, 'MINUTE', javascriptGenerator.ORDER_ATOMIC) || '0'
    const second = generator.valueToCode(block, 'SECOND', javascriptGenerator.ORDER_ATOMIC) || '0'
    return [`new Date(${year}, ${month}-1, ${day}, ${hour}, ${minute}, ${second}).toISOString()`, javascriptGenerator.ORDER_ATOMIC]
}

// Получить компонент даты
Blockly.Blocks['date_get'] = {
    init: function() {
        this.appendValueInput('DATE').setCheck('String').appendField('из даты')
        this.appendDummyInput()
            .appendField(new Blockly.FieldDropdown([
                ['год', 'getFullYear'],
                ['месяц', 'getMonth'],
                ['день', 'getDate'],
                ['час', 'getHours'],
                ['минута', 'getMinutes'],
                ['секунда', 'getSeconds']
            ]), 'PART')
        this.setOutput(true, 'Number')
        this.setColour(230)
        this.setTooltip('Получить компонент даты')
    }
}

javascriptGenerator.forBlock['date_get'] = function(block, generator) {
    const date = generator.valueToCode(block, 'DATE', javascriptGenerator.ORDER_ATOMIC) || 'new Date()'
    const part = block.getFieldValue('PART')
    return [`new Date(${date}).${part}()`, javascriptGenerator.ORDER_ATOMIC]
}

// Изменить дату
Blockly.Blocks['date_change'] = {
    init: function() {
        this.appendValueInput('DATE').setCheck('String').appendField('изменить дату')
        this.appendDummyInput()
            .appendField(new Blockly.FieldDropdown([
                ['дни', 'days'],
                ['месяцы', 'months'],
                ['годы', 'years']
            ]), 'UNIT')
        this.appendValueInput('AMOUNT').setCheck('Number').appendField('на')
        this.setOutput(true, 'String')
        this.setColour(230)
        this.setTooltip('Изменить дату на указанное количество')
    }
}

javascriptGenerator.forBlock['date_change'] = function(block, generator) {
    const date = generator.valueToCode(block, 'DATE', javascriptGenerator.ORDER_ATOMIC) || 'new Date()'
    const unit = block.getFieldValue('UNIT')
    const amount = generator.valueToCode(block, 'AMOUNT', javascriptGenerator.ORDER_ATOMIC) || '0'

    const code = `(function() {
        var d = new Date(${date});
        d.set${unit === 'days' ? 'Date' : unit === 'months' ? 'Month' : 'FullYear'}(
            d.${unit === 'days' ? 'getDate' : unit === 'months' ? 'getMonth' : 'getFullYear'}() + ${amount}
        );
        return d.toISOString();
    })()`

    return [code, javascriptGenerator.ORDER_ATOMIC]
}

// Форматировать дату
Blockly.Blocks['date_format'] = {
    init: function() {
        this.appendValueInput('DATE').setCheck('String').appendField('формат даты')
        this.appendDummyInput()
            .appendField(new Blockly.FieldDropdown([
                ['DD.MM.YYYY', 'ddmmyyyy'],
                ['YYYY-MM-DD', 'yyyymmdd'],
                ['DD/MM/YYYY', 'ddmmyyyy2'],
                ['текст', 'text']
            ]), 'FORMAT')
        this.setOutput(true, 'String')
        this.setColour(230)
        this.setTooltip('Форматировать дату')
    }
}

javascriptGenerator.forBlock['date_format'] = function(block, generator) {
    const date = generator.valueToCode(block, 'DATE', javascriptGenerator.ORDER_ATOMIC) || 'new Date()'
    const format = block.getFieldValue('FORMAT')
    const d = `new Date(${date})`
    const dd = `${d}.getDate()`
    const mm = `${d}.getMonth()+1`
    const yyyy = `${d}.getFullYear()`
    if (format === 'ddmmyyyy') return [`\`\${${dd}}.\${String(${mm}).padStart(2,'0')}.\${${yyyy}}\``, javascriptGenerator.ORDER_ATOMIC]
    if (format === 'yyyymmdd') return [`\`\${${yyyy}}-\${String(${mm}).padStart(2,'0')}-\${${dd}}\``, javascriptGenerator.ORDER_ATOMIC]
    if (format === 'ddmmyyyy2') return [`\`\${${dd}}/\${String(${mm}).padStart(2,'0')}/\${${yyyy}}\``, javascriptGenerator.ORDER_ATOMIC]
    return [`${d}.toLocaleDateString('ru-RU')`, javascriptGenerator.ORDER_ATOMIC]
}

// Установить компонент даты
Blockly.Blocks['date_set'] = {
    init: function() {
        this.appendValueInput('DATE').setCheck('String').appendField('установить в дате')
        this.appendDummyInput()
            .appendField(new Blockly.FieldDropdown([
                ['год', 'FullYear'],
                ['месяц', 'Month'],
                ['день', 'Date'],
                ['час', 'Hours'],
                ['минуту', 'Minutes'],
                ['секунду', 'Seconds']
            ]), 'PART')
        this.appendValueInput('VALUE').setCheck('Number').appendField('значение')
        this.setOutput(true, 'String')
        this.setColour(230)
        this.setTooltip('Установить компонент даты')
    }
}

javascriptGenerator.forBlock['date_set'] = function(block, generator) {
    const date = generator.valueToCode(block, 'DATE', javascriptGenerator.ORDER_ATOMIC) || 'new Date()'
    const part = block.getFieldValue('PART')
    const value = generator.valueToCode(block, 'VALUE', javascriptGenerator.ORDER_ATOMIC) || '0'
    const code = `(function() {
        var d = new Date(${date});
        d.set${part}(${value});
        return d.toISOString();
    })()`
    return [code, javascriptGenerator.ORDER_ATOMIC]
}

Blockly.Blocks['date_add'] = {
    init: function() {
        this.appendValueInput('DATE').setCheck('String').appendField('увеличить дату')
        this.appendValueInput('AMOUNT').setCheck('Number').appendField('на')
        this.appendDummyInput()
            .appendField(new Blockly.FieldDropdown([
                ['дней', 'Days'],
                ['месяцев', 'Months'],
                ['лет', 'FullYear']
            ]), 'UNIT')
        this.setOutput(true, 'String')
        this.setColour(230)
        this.setTooltip('Увеличить дату на указанное количество')
    }
}

javascriptGenerator.forBlock['date_add'] = function(block, generator) {
    const date = generator.valueToCode(block, 'DATE', javascriptGenerator.ORDER_ATOMIC) || 'new Date()'
    const amount = generator.valueToCode(block, 'AMOUNT', javascriptGenerator.ORDER_ATOMIC) || '0'
    const unit = block.getFieldValue('UNIT')
    const code = `(function() {
        var d = new Date(${date});
        d.set${unit}(d.get${unit}() + ${amount});
        return d.toISOString();
    })()`
    return [code, javascriptGenerator.ORDER_ATOMIC]
}


Blockly.Blocks['date_subtract'] = {
    init: function() {
        this.appendValueInput('DATE').setCheck('String').appendField('уменьшить дату')
        this.appendValueInput('AMOUNT').setCheck('Number').appendField('на')
        this.appendDummyInput()
            .appendField(new Blockly.FieldDropdown([
                ['дней', 'Days'],
                ['месяцев', 'Months'],
                ['лет', 'FullYear']
            ]), 'UNIT')
        this.setOutput(true, 'String')
        this.setColour(230)
        this.setTooltip('Уменьшить дату на указанное количество')
    }
}

javascriptGenerator.forBlock['date_subtract'] = function(block, generator) {
    const date = generator.valueToCode(block, 'DATE', javascriptGenerator.ORDER_ATOMIC) || 'new Date()'
    const amount = generator.valueToCode(block, 'AMOUNT', javascriptGenerator.ORDER_ATOMIC) || '0'
    const unit = block.getFieldValue('UNIT')
    const code = `(function() {
        var d = new Date(${date});
        d.set${unit}(d.get${unit}() - ${amount});
        return d.toISOString();
    })()`
    return [code, javascriptGenerator.ORDER_ATOMIC]
}

// Разница между датами
Blockly.Blocks['date_diff'] = {
    init: function() {
        this.appendValueInput('DATE1').setCheck('String').appendField('разница между')
        this.appendValueInput('DATE2').setCheck('String').appendField('и')
        this.appendDummyInput()
            .appendField(new Blockly.FieldDropdown([
                ['дни', 'days'],
                ['месяцы', 'months'],
                ['годы', 'years']
            ]), 'UNIT')
        this.setOutput(true, 'Number')
        this.setColour(230)
        this.setTooltip('Разница между двумя датами')
    }
}

javascriptGenerator.forBlock['date_diff'] = function(block, generator) {
    const date1 = generator.valueToCode(block, 'DATE1', javascriptGenerator.ORDER_ATOMIC) || 'new Date()'
    const date2 = generator.valueToCode(block, 'DATE2', javascriptGenerator.ORDER_ATOMIC) || 'new Date()'
    const unit = block.getFieldValue('UNIT')
    const diff = `new Date(${date1}) - new Date(${date2})`
    if (unit === 'days') return [`Math.round((${diff}) / (1000*60*60*24))`, javascriptGenerator.ORDER_ATOMIC]
    if (unit === 'months') return [`Math.round((${diff}) / (1000*60*60*24*30))`, javascriptGenerator.ORDER_ATOMIC]
    return [`Math.round((${diff}) / (1000*60*60*24*365))`, javascriptGenerator.ORDER_ATOMIC]
}

// Сравнить даты
Blockly.Blocks['date_compare'] = {
    init: function() {
        this.appendValueInput('DATE1').setCheck('String').appendField('сравнить')
        this.appendValueInput('DATE2').setCheck('String')
        this.appendDummyInput()
            .appendField(new Blockly.FieldDropdown([
                ['<', '<'],
                ['>', '>'],
                ['<=', '<='],
                ['>=', '>='],
                ['==', '=='],
                ['!=', '!=']
            ]), 'OP')
        this.setOutput(true, 'Boolean')
        this.setColour(230)
        this.setTooltip('Сравнить две даты')
    }
}

javascriptGenerator.forBlock['date_compare'] = function(block, generator) {
    const date1 = generator.valueToCode(block, 'DATE1', javascriptGenerator.ORDER_ATOMIC) || 'new Date()'
    const date2 = generator.valueToCode(block, 'DATE2', javascriptGenerator.ORDER_ATOMIC) || 'new Date()'
    const op = block.getFieldValue('OP')
    return [`new Date(${date1}) ${op} new Date(${date2})`, javascriptGenerator.ORDER_RELATIONAL]
}



// Выполнить процедуру
// Выполнить процедуру
let savedProceduresCache = []

Blockly.Blocks['action_execute'] = {
    init: function() {
        this.appendDummyInput()
            .appendField('выполнить процедуру')
            .appendField(new Blockly.FieldDropdown(function() {
                if (savedProceduresCache.length === 0) {
                    return [['загрузка...', '']]
                }
                return savedProceduresCache.map(p => [p.name, p.name])
            }), 'PROCEDURE')
        this.setPreviousStatement(true, null)
        this.setNextStatement(true, null)
        this.setColour(200)
        this.setTooltip('Выбрать и выполнить сохранённую процедуру')
    }
}

javascriptGenerator.forBlock['action_execute'] = function(block) {
    const procedure = block.getFieldValue('PROCEDURE')
    if (!procedure) return '// процедура не выбрана\n'
    return `executeProcedure('${procedure}');\n`
}

// Функция обновления кэша процедур (вызывается при загрузке workspace)
function refreshSavedProceduresCache(workspace) {
    import('../api/savedProcedures.js').then(({ fetchSavedProcedures }) => {
        fetchSavedProcedures().then(procedures => {
            savedProceduresCache = procedures || []
            // Обновляем все блоки action_execute на workspace
            const blocks = workspace.getAllBlocks(false)
            for (const block of blocks) {
                if (block.type === 'action_execute') {
                    const dropdown = block.getField('PROCEDURE')
                    if (dropdown) {
                        const options = savedProceduresCache.length === 0
                            ? [['нет процедур', '']]
                            : savedProceduresCache.map(p => [p.name, p.name])
                        dropdown.setOptions(options)
                    }
                }
            }
        }).catch(err => console.warn('Failed to load saved procedures:', err))
    })
}

export { refreshSavedProceduresCache }