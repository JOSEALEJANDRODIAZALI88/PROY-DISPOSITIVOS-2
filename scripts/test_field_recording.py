import importlib.util
from pathlib import Path
import sqlite3
import sys
import unittest

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("field_review", Path(__file__).with_name("check-field-recording.py"))
field = importlib.util.module_from_spec(spec)
spec.loader.exec_module(field)


class FieldRecordingTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.addCleanup(self.db.close)
        self.db.executescript("""
            CREATE TABLE activity_sessions(id TEXT, state TEXT, durationMillis INTEGER, endedAt INTEGER);
            CREATE TABLE route_points(id INTEGER PRIMARY KEY,sessionId TEXT,timeMillis INTEGER);
            INSERT INTO activity_sessions VALUES('test','FINISHED',1200000,1200000);
        """)

    def points(self, times):
        self.db.executemany("INSERT INTO route_points(sessionId,timeMillis) VALUES('test',?)",
                            [(value,) for value in times])
        return field.review(self.db, "test")

    def test_continuous_synthetic_data_does_not_claim_physical_evidence(self):
        result = self.points(range(0, 1200001, 5000))
        self.assertTrue(result["continuous_20min_gps_criterion"])
        self.assertFalse(result["physical_device_proven"])
        self.assertFalse(result["screen_off_proven"])
        self.assertFalse(result["classification_and_distance_accuracy_proven"])
        self.assertEqual(result["maximum_gap_ms"], 5000)

    def test_thirty_second_boundary_allowed(self):
        self.assertTrue(self.points(range(0, 1200001, 30000))["continuous_20min_gps_criterion"])

    def test_gap_over_limit_rejected(self):
        result = self.points([0, 30001, 1200000])
        self.assertFalse(result["continuous_20min_gps_criterion"])
        self.assertEqual(result["gaps_over_30_seconds"], 2)

    def test_clock_rollback_or_duplicate_rejected(self):
        result = self.points([0, 5000, 5000, 1200000])
        self.assertFalse(result["timestamps_strictly_increasing"])
        self.assertIsNone(result["maximum_gap_ms"])
        self.assertFalse(result["continuous_20min_gps_criterion"])

    def test_empty_short_or_unfinished_not_approved(self):
        self.assertFalse(self.points([])["continuous_20min_gps_criterion"])
        self.db.execute("UPDATE activity_sessions SET state='ACTIVE', endedAt=NULL")
        self.assertFalse(self.points(range(0, 1200001, 5000))["continuous_20min_gps_criterion"])

    def test_missing_session_and_sql_injection_cannot_select_another(self):
        with self.assertRaises(RuntimeError):
            field.review(self.db, "' OR 1=1 --")

    def test_report_contains_no_identifiers_coordinates_or_health(self):
        result = self.points([0, 5000])
        text = str(result)
        for private in ("latitude", "longitude", "weight", "height", "session_id"):
            self.assertNotIn(private, text)


if __name__ == "__main__":
    unittest.main()
