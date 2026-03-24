from fastapi import FastAPI
from pydantic import BaseModel
import re

app = FastAPI()


class LocationInput(BaseModel):
    raw_text: str


class ParsedLocation(BaseModel):
    building: str | None
    room: str | None
    floor: int | None


def extract_entities(text: str) -> ParsedLocation:
    norm_text = text.lower()

    building = None
    room = None
    floor = None

    #FLOOR EXTRACTION
    floor_match = re.search(r'(\d+)(?:st|nd|rd|th)?\s*(?:floor|fl|όροφος)|(?:floor|fl|όροφος)\s*(\d+)', norm_text)
    if floor_match:
        floor = int(floor_match.group(1) or floor_match.group(2))

    #BUILDING EXTRACTION
    parts = [p.strip() for p in text.split(',')]
    for part in parts:
        lower_part = part.lower()
        if any(keyword in lower_part for keyword in ['building', 'campus', 'κτήριο']):
            building = part
            break

    #ROOM EXTRACTION
    for part in parts:
        lower_part = part.lower()
        if building and part == building:
            continue
        if any(keyword in lower_part for keyword in ['lab', 'room', 'amphitheater', 'αίθουσα', 'εργαστήριο', 'αμφιθέατρο']):
            clean_room = re.sub(r'(\d+)(?:st|nd|rd|th)?\s*(?:floor|fl|όροφος)', '', part, flags=re.IGNORECASE).strip()
            room = re.sub(r'\s+', ' ', clean_room)
            break

    #FALLBACK
    if not room and parts:
        room = parts[0]

    return ParsedLocation(building=building, room=room, floor=floor)


@app.post("/parse-location", response_model=ParsedLocation)
def parse_location(data: LocationInput):
    return extract_entities(data.raw_text)


if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)