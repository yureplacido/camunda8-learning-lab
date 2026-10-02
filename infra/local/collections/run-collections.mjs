#!/usr/bin/env node
// Executa a coleção Postman contra o cluster local e avalia os scripts de teste.
//
//   node infra/local/collections/run-collections.mjs
//   node infra/local/collections/run-collections.mjs --folder "20 — Deploy"
//
// Por que isso existe: os scripts de teste de uma coleção só valem alguma coisa
// depois de rodarem. Sem um runner no repositório, a prova depende de alguém
// abrir o Postman na mão — e aí ninguém registra a evidência.
//
// Este runner é deliberadamente mínimo e sem dependências: implementa só o
// subconjunto da API do Postman e do Chai que a coleção usa. Se um request
// precisar de um matcher que não existe aqui, o erro é explícito em vez de
// silencioso. Um runner que finge suportar tudo é pior do que nenhum.

import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, resolve, relative } from 'node:path';

const HERE = dirname(fileURLToPath(import.meta.url));
const REPO = resolve(HERE, '../../..');
const COLLECTION = resolve(HERE, 'camunda8-lab.postman_collection.json');

// ---------------------------------------------------------------- matcher

// O sandbox do Postman é um módulo com top-level await, então o corpo do
// script de teste precisa rodar dentro de uma função async.
const AsyncFunction = Object.getPrototypeOf(async function () {}).constructor;

class Expectation {
  constructor(actual, negated = false) {
    this.actual = actual;
    this.negated = negated;
  }

  get not() {
    return new Expectation(this.actual, !this.negated);
  }

  get to() { return this; }
  get be() { return this; }
  get been() { return this; }
  get that() { return this; }
  get and() { return this; }
  get has() { return this; }
  get have() { return this; }
  get with() { return this; }
  get deep() { return this; }

  // typeof devolve 'object' para array, então 'array' é tratado à parte.
  // Sem isso, to.be.an('array') nunca passaria e o runner acusaria a
  // coleção de um erro que ela não tem.
  typeOf(value) {
    if (Array.isArray(value)) return 'array';
    if (value === null) return 'null';
    return typeof value;
  }

  get a() {
    return (type) => this._assert(
      this.typeOf(this.actual) === type,
      `esperava tipo ${type}, veio ${this.typeOf(this.actual)}`);
  }

  get an() { return this.a; }

  eql(expected) {
    const ok = JSON.stringify(this.actual) === JSON.stringify(expected);
    return this._assert(ok, `esperava ${JSON.stringify(expected)}, veio ${JSON.stringify(this.actual)}`);
  }

  equal(expected) { return this.eql(expected); }

  include(needle) {
    let ok;
    if (typeof this.actual === 'string') ok = this.actual.includes(needle);
    else if (Array.isArray(this.actual)) ok = this.actual.includes(needle);
    else throw new Error('include() exige string ou array');
    return this._assert(ok, `esperava conter ${JSON.stringify(needle)}`);
  }

  above(n) { return this._assert(this.actual > n, `esperava ${this.actual} > ${n}`); }
  below(n) { return this._assert(this.actual < n, `esperava ${this.actual} < ${n}`); }
  least(n) { return this._assert(this.actual >= n, `esperava ${this.actual} >= ${n}`); }
  most(n) { return this._assert(this.actual <= n, `esperava ${this.actual} <= ${n}`); }
  within(a, b) { return this._assert(this.actual >= a && this.actual <= b, `esperava ${this.actual} entre ${a} e ${b}`); }
  aboveOrEqual(n) { return this.least(n); }

  property(key) {
    const ok = this.actual !== null && typeof this.actual === 'object' && key in this.actual;
    return this._assert(ok, `esperava a propriedade ${key}`);
  }

  match(re) {
    const ok = re instanceof RegExp && re.test(String(this.actual));
    return this._assert(ok, `esperava ${JSON.stringify(this.actual)} casando ${re}`);
  }

  throw() {
    let threw = false;
    try { this.actual(); } catch { threw = true; }
    return this._assert(threw, 'esperava que a função lançasse');
  }

  _assert(ok, message) {
    if (this.negated ? ok : !ok) throw new Error(message);
    return this;
  }
}

const expect = (actual) => new Expectation(actual);

// ------------------------------------------------------------------ runner

function interpolate(str, vars) {
  return str.replace(/\{\{(\w+)\}\}/g, (_, k) => {
    if (!(k in vars)) throw new Error(`variável não declarada: {{${k}}}`);
    if (vars[k] === '') {
      throw new Error(
        `variável vazia: {{${k}}} — ela é capturada por uma pasta anterior. ` +
        `Rode a coleção inteira (ou a pasta que a captura) em vez de ${argFolder ?? 'esta pasta'} isolada.`
      );
    }
    return vars[k];
  });
}

function readSpec(path) {
  const abs = resolve(REPO, interpolate(path, vars));
  return readFileSync(abs, 'utf8');
}

// pm.sendRequest, na forma que o sandbox do Postman aceita: devolve uma
// Promise e também aceita callback. A coleção usa isso para esperar o
// rdbms exporter projetar a instância no H2 antes de exigir o GET.
function sendRequest(options, cb) {
  const opts = typeof options === 'string' ? { url: options, method: 'GET' } : options;
  const promise = (async () => {
    const res = await fetch(opts.url, {
      method: opts.method ?? 'GET',
      headers: opts.header,
      body: opts.body,
    });
    const body = await res.text();
    const bag = new Map([...res.headers].map(([k, v]) => [k.toLowerCase(), v]));
    return {
      code: res.status,
      text: () => body,
      json: () => JSON.parse(body),
      headers: { get: (k) => bag.get(k.toLowerCase()) },
    };
  })();
  if (typeof cb === 'function') promise.then(cb);
  return promise;
}

const delay = (ms) => new Promise((r) => setTimeout(r, ms));

const collection = JSON.parse(readFileSync(COLLECTION, 'utf8'));
const vars = Object.fromEntries(collection.variable.map((v) => [v.key, v.value]));

const argFolder = process.argv.includes('--folder')
  ? process.argv[process.argv.indexOf('--folder') + 1]
  : null;

const red = (s) => `\x1b[31m${s}\x1b[0m`;
const green = (s) => `\x1b[32m${s}\x1b[0m`;
const dim = (s) => `\x1b[2m${s}\x1b[0m`;

let totalAssertions = 0;
let failedAssertions = 0;
let failedRequests = 0;
let ranRequests = 0;

for (const folder of collection.item) {
  if (argFolder && !folder.name.includes(argFolder)) continue;

  console.log(`\n${folder.name}`);
  console.log(dim('-'.repeat(folder.name.length)));

  for (const item of folder.item) {
    const { method, url, body, header = [] } = item.request;

    let target;
    let headers = {};
    let payload;
    try {
      target = interpolate(url.raw ?? url, vars);
      for (const h of header) headers[h.key] = h.value;
      if (body?.mode === 'raw') {
        payload = interpolate(body.raw, vars);
        headers['Content-Type'] ??= 'application/json';
      } else if (body?.mode === 'formdata') {
        payload = new FormData();
        for (const field of body.formdata) {
          if (field.type === 'file') {
            // O nome do arquivo precisa ser o caminho já interpolado: mandar
            // literalmente "{{bpmnPath}}" como filename chega sem extensão .bpmn
            // e o cluster rejeita o deploy com 400.
            const srcPath = interpolate(field.src, vars);
            payload.append(field.key, new Blob([readSpec(srcPath)]), srcPath.split('/').pop());
          } else {
            payload.append(field.key, field.value);
          }
        }
      }
    } catch (err) {
      console.log(`  ${red('PULADO')} ${item.name}: ${err.message}`);
      failedRequests += 1;
      continue;
    }

    let response;
    try {
      response = await fetch(target, { method, headers, body: payload });
    } catch (err) {
      console.log(`  ${red('ERRO')} ${item.name}: ${err.message}`);
      failedRequests += 1;
      continue;
    }

    const text = await response.text();
    const headerBag = new Map(
      [...response.headers].map(([k, v]) => [k.toLowerCase(), v])
    );

    const pending = [];
    const pm = {
      request: {
        method,
        // pm.request.url.toString() precisa devolver a URL já interpolada,
        // porque é assim que a coleção se referencia para repolar o request.
        url: { raw: target, toString: () => target },
        // pm.request.body.raw é o corpo já interpolado: reenviá-lo sem
        // reinterpolar evita que uma variável vire duplo-template.
        body: { raw: payload ?? '', mode: body?.mode },
      },
      response: {
        code: response.status,
        text: () => text,
        json: () => JSON.parse(text),
        headers: { get: (k) => headerBag.get(k.toLowerCase()) },
      },
      collectionVariables: {
        get: (k) => vars[k],
        set: (k, v) => { vars[k] = v; },
      },
      sendRequest,
      expect,
      test(name, fn) {
        totalAssertions += 1;
        // Um callback de teste pode ser async: o sandbox do Postman aguenta, e
        // a coleção usa isso para pollar o secondary storage. A ordem de
        // impressão é preservada porque o resumo só é impresso no fim.
        pending.push(
          Promise.resolve()
            .then(fn)
            .then(
              () => ({ ok: true, name }),
              (err) => {
                failedAssertions += 1;
                return { ok: false, name, err: err.message };
              }
            )
        );
      },
    };

    const scripts = (item.event ?? []).filter((e) => e.listen === 'test');
    let scriptError = null;
    for (const s of scripts) {
      // AsyncFunction, não Function: o sandbox do Postman executa o script
      // como corpo de função async, e a coleção usa await no topo do script.
      // eslint-disable-next-line no-new-func
      const body_ = new AsyncFunction('pm', s.script.exec.join('\n'));
      // O await aqui é obrigatório, não cosmético. A coleção usa await no topo
      // do script para pollar o secondary storage. Sem esperar, o runner
      // seguiria para o próximo request com o script ainda suspenso e
      // Promise.all(pending) rodaria com a lista vazia: o request sairia
      // verde sem ter executado uma única asserção.
      try {
        await body_(pm);
      } catch (err) {
        // Um script que estoura tem de reprovar o request. Deixar passar
        // transformaria um bug da coleção em request verde.
        scriptError = err.message;
      }
    }
    const results = await Promise.all(pending);
    if (scriptError) {
      failedAssertions += 1;
      results.push({ ok: false, name: 'script de teste', err: scriptError });
    }

    const failed = results.filter((r) => !r.ok);
    if (failed.length > 0) failedRequests += 1;
    ranRequests += 1;

    const mark = failed.length === 0 ? green('ok  ') : red('FALHA');
    console.log(`  ${mark} ${response.status} ${item.name}`);
    if (failed.length > 0) {
      // Sem a URL, um 404 numa coleção grande é um mistério. Ela é curta e
      // quase sempre mostra a variável interpolada errada na hora.
      console.log(dim(`        → ${method} ${target}`));
    }
    for (const r of results) {
      console.log(dim(`        · ${r.name}${r.ok ? '' : ` -> ${r.err}`}`));
    }
  }
}

console.log();
console.log('='.repeat(64));
console.log(`${ranRequests} request(s), ${totalAssertions} asserção(ões), ${failedRequests} request(s) com falha`);
console.log(dim(`variáveis capturadas: ${Object.entries(vars)
  .filter(([k, v]) => ['processDefinitionKey', 'processDefinitionId', 'processInstanceKey', 'processDefinitionVersion'].includes(k))
  .map(([k, v]) => `${k}=${v}`).join('  ') || '(nenhuma)'}`));
console.log(relative(REPO, COLLECTION));

process.exit(failedRequests > 0 ? 1 : 0);