"""Quick test auto crawl for a city."""
import sys

from app.services.corpus_service import count_destination_hits, ensure_destination_corpus

city = sys.argv[1] if len(sys.argv) > 1 else "厦门"
r = ensure_destination_corpus(city)
print("result:", r)
print("hits:", count_destination_hits(city))
