import importlib.util
import json
import tempfile
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from pathlib import Path
from contextlib import contextmanager

SPEC = importlib.util.spec_from_file_location("nvserver", Path(__file__).resolve().parents[1] / "server" / "newsvoice_server.py")
server = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(server)
TOKEN="test_token_123456789"

@contextmanager
def running():
    with tempfile.TemporaryDirectory() as td:
        http=ThreadingHTTPServer(("127.0.0.1",0),server.handler_factory(TOKEN,Path(td)))
        t=threading.Thread(target=http.serve_forever,daemon=True)
        t.start()
        try:yield "http://127.0.0.1:"+str(http.server_port)
        finally:http.shutdown();http.server_close();t.join(timeout=3)

def request(url,method="GET",data=None,content_type=None,token=TOKEN):
    h={"Authorization":"Bearer "+token}
    if content_type:h["Content-Type"]=content_type
    return urllib.request.urlopen(urllib.request.Request(url,method=method,data=data,headers=h),timeout=5)

class ServerTest(unittest.TestCase):
    def test_pipeline(self):
        with running() as base:
            with self.assertRaises(urllib.error.HTTPError) as e:request(base+"/api/jobs/next",token="bad")
            self.assertEqual(e.exception.code,401)
            self.assertEqual(request(base+"/api/jobs/next").status,204)
            payload=json.dumps({"id":"20261121_am","text":"안녕하십니까. 필리핀 뉴스입니다."},ensure_ascii=False).encode()
            self.assertEqual(request(base+"/api/jobs",method="POST",data=payload,content_type="application/json").status,201)
            result=json.load(request(base+"/api/jobs/next"))
            self.assertEqual(result["id"],"20261121_am")
            self.assertEqual(request(base+"/api/jobs/next").status,204)
            with self.assertRaises(urllib.error.HTTPError) as e:
                request(base+"/api/jobs/20261121_am/audio",method="POST",data=b"bad",content_type="audio/wav")
            self.assertEqual(e.exception.code,413)
            wav=b"RIFF"+b"\x24\x00\x00\x00"+b"WAVE"+b"\x00"*48
            self.assertEqual(request(base+"/api/jobs/20261121_am/audio",method="POST",data=wav,content_type="audio/wav").status,201)
            self.assertEqual(request(base+"/api/jobs/next").status,204)

if __name__=="__main__": unittest.main()
