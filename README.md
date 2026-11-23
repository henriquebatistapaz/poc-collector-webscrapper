# poc-collector-webscrapper

Projeto local com dois scrapers independentes: Java 27/Maven e Python 3.12. A primeira fase faz parsing próprio de HTML local, monta modelos tipados e emite JSON Lines no contrato comum em `contracts/`. `entities` começa vazio; regras de extração pertencem à fase seguinte.

As fontes Hacker News e WordPress News estão catalogadas, mas a coleta ao vivo permanece desabilitada até a revisão de termos/políticas. Os exemplos são fixtures sintéticas; nenhum comando padrão acessa sites externos.

## Java

```bash
cd scraper-java
mvn -B -ntp verify
mvn -q exec:java -Dexec.mainClass=dev.henrique.collectorwebscrapper.Main -Dexec.args="parse hacker-news https://news.ycombinator.com/item?id=123456 ../contracts/fixtures/hacker-news-item.html https://news.ycombinator.com/item?id=123456 ../contracts/fixtures/hacker-news-item.html"
```

## Python

```bash
cd scraper-python
python3.12 -m venv .venv
.venv/bin/python -m pip install -r requirements.lock
.venv/bin/python -m pip install -e . --no-deps
.venv/bin/python -m pytest -q
.venv/bin/webscrapper --source wordpress-news --url https://wordpress.org/news/2026/11/exemplo/ --input ../contracts/fixtures/wordpress-news-item.html --url https://wordpress.org/news/2026/11/exemplo/ --input ../contracts/fixtures/wordpress-news-item.html
```

Cada execução aceita pares de página URL/arquivo local e produz um envelope por linha JSONL, deduplicado por `(source, externalId)` dentro do lote. Texto integral e comentários existem apenas no objeto transitório em memória e não são serializados nem registrados. O link externo do Hacker News é preservado no item e nunca consultado. As verificações são locais; o projeto não configura GitHub Actions.

O contexto AI-init canônico e as specs ficam no repositório sibling `ai-init-internal/poc-collector-webscrapper`; esses arquivos geridos não são versionados neste projeto (`versioned: false`).
