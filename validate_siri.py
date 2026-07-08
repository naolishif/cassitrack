"""
Valida un file SIRI XML contro lo schema XSD ufficiale.

Uso:
    python validate_siri.py <percorso-siri.xsd> <percorso-siri.xml>

Esempio (Windows):
    python validate_siri.py "C:\\Users\\giaco\\Desktop\\SIRI-main\\siri.xsd" "C:\\Users\\giaco\\Desktop\\siri.xml"

Se non passi argomenti, cerca 'siri.xsd' e 'siri.xml' nella cartella corrente.

Prerequisito:  pip install lxml
"""
import sys

try:
    from lxml import etree
except ImportError:
    sys.exit("Manca la libreria lxml. Installala con:  pip install lxml")

xsd_path = sys.argv[1] if len(sys.argv) > 1 else "siri.xsd"
xml_path = sys.argv[2] if len(sys.argv) > 2 else "siri.xml"

# 1) Carica lo schema
try:
    schema = etree.XMLSchema(etree.parse(xsd_path))
except Exception as e:
    sys.exit(f"Impossibile caricare lo schema '{xsd_path}':\n  {e}\n"
             f"(assicurati che tutti i file .xsd siano nella stessa cartella di siri.xsd)")

# 2) Carica il tuo SIRI
try:
    doc = etree.parse(xml_path)
except Exception as e:
    sys.exit(f"Impossibile leggere '{xml_path}':\n  {e}")

# 3) Valida
if schema.validate(doc):
    print("VALIDO  ✅   il pacchetto SIRI rispetta completamente lo schema.")
else:
    print("NON valido  ❌   errori trovati (in ordine):\n")
    for err in schema.error_log:
        print(f"  riga {err.line}: {err.message}")
    print(f"\nTotale problemi: {len(schema.error_log)}")
    sys.exit(1)
