import os
from pathlib import Path

from dotenv import load_dotenv
from sqlalchemy import create_engine, text
from sqlalchemy.engine import Engine

load_dotenv(Path(__file__).resolve().parent.parent / ".env")


def _resolve_url() -> str:
    url = os.environ.get("DATABASE_URL")
    if not url:
        raise RuntimeError("DATABASE_URL is not set; check the project root .env")
    if url.startswith("postgresql://"):
        url = url.replace("postgresql://", "postgresql+psycopg://", 1)
    return url


def get_engine() -> Engine:
    return create_engine(_resolve_url(), future=True)


if __name__ == "__main__":
    with get_engine().connect() as conn:
        print("SELECT 1 ->", conn.execute(text("SELECT 1")).scalar())
