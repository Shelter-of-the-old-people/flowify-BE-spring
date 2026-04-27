import os
import requests
import io
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart
from canvasapi import Canvas
from google.oauth2.credentials import Credentials
from googleapiclient.discovery import build
from googleapiclient.http import MediaIoBaseUpload
from collections import defaultdict
from datetime import datetime
from dotenv import load_dotenv

# .env 파일 로드
load_dotenv()

# --- [환경 변수 설정] ---
CANVAS_API_URL = os.getenv("CANVAS_API_URL", "https://canvas.kumoh.ac.kr")
CANVAS_API_KEY = os.getenv("CANVAS_TOKEN")
GOOGLE_TOKEN_FILE = os.getenv("GOOGLE_TOKEN_FILE", "token.json")

# 이메일 설정 (Flowify 방식)
EMAIL_SENDER = os.getenv("EMAIL_SENDER")
EMAIL_PASSWORD = os.getenv("EMAIL_PASSWORD")
EMAIL_RECEIVER = os.getenv("EMAIL_RECEIVER")

def get_drive_service():
    """구글 드라이브 서비스만 빌드합니다. (메일은 SMTP 사용)"""
    creds = Credentials.from_authorized_user_file(GOOGLE_TOKEN_FILE)
    return build('drive', 'v3', credentials=creds)

def get_or_create_folder(service, folder_name, parent_id=None):
    """폴더를 찾고 없으면 생성하여 ID를 반환합니다."""
    query = f"name = '{folder_name}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
    if parent_id:
        query += f" and '{parent_id}' in parents"

    results = service.files().list(q=query, fields="files(id)", supportsAllDrives=True).execute().get('files', [])

    if results:
        return results[0]['id']
    else:
        print(f"  📂 새 폴더 생성 중: {folder_name}")
        file_metadata = {'name': folder_name, 'mimeType': 'application/vnd.google-apps.folder'}
        if parent_id:
            file_metadata['parents'] = [parent_id]
        folder = service.files().create(body=file_metadata, fields='id', supportsAllDrives=True).execute()
        return folder.get('id')

def send_summary_email_smtp(summary_data):
    """민호님의 Flowify SMTP 방식을 사용하여 결과를 전송합니다."""
    if not summary_data:
        print("ℹ️ 새로 백업된 파일이 없어 메일을 보내지 않습니다.")
        return

    now_str = datetime.now().strftime('%Y-%m-%d %H:%M')

    # HTML 본문 구성
    content_html = f"""
    <div style="font-family: 'Malgun Gothic', dotum, sans-serif; max-width: 800px; margin: auto; border: 1px solid #ddd; padding: 20px; border-radius: 8px; line-height: 1.6;">
        <h2 style="color: #004a95; border-bottom: 3px solid #004a95; padding-bottom: 10px;">🎓 LMS 강의자료 백업 완료</h2>
        <p style="color: #666; font-size: 0.9em;">업데이트 시간: {now_str}</p>
    """

    for course, files in summary_data.items():
        content_html += f"""
        <div style="margin-bottom: 20px; padding: 15px; background-color: #f9f9f9; border-left: 6px solid #004a95; border-radius: 4px;">
            <strong style="font-size: 1.1em; color: #333;">📂 {course}</strong>
            <ul style="margin-top: 10px; color: #444;">
        """
        for f in files:
            content_html += f"<li>{f}</li>"
        content_html += "</ul></div>"

    content_html += """
        <hr style="border: 0.5px solid #eee; margin: 30px 0;">
        <p style="font-size: 0.8em; color: #999; text-align: center;">본 메일은 민호님의 캡스톤 프로젝트 자동화 노드에서 발송되었습니다.</p>
    </div>
    """

    msg = MIMEMultipart()
    msg['Subject'] = f"🚀 [LMS 백업 완료] {now_str} 업데이트 리포트"
    msg['From'] = EMAIL_SENDER
    msg['To'] = EMAIL_RECEIVER
    msg.attach(MIMEText(content_html, 'html'))

    try:
        with smtplib.SMTP_SSL("smtp.gmail.com", 465) as server:
            server.login(EMAIL_SENDER, EMAIL_PASSWORD)
            server.sendmail(EMAIL_SENDER, EMAIL_RECEIVER, msg.as_string())
        print(f"📧 결과 보고서가 {EMAIL_RECEIVER}로 발송되었습니다!")
    except Exception as e:
        print(f"❌ 메일 발송 실패: {e}")

def main():
    # .env에서 읽어온 토큰으로 Canvas 객체 생성
    canvas = Canvas(CANVAS_API_URL, CANVAS_API_KEY)
    drive_service = get_drive_service()

    # 1. 최상위 루트 폴더 생성/확인
    root_folder_name = os.getenv("ROOT_FOLDER_NAME", "LMS_강의자료_백업")
    root_folder_id = get_or_create_folder(drive_service, root_folder_name)

    print("\n🔍 Canvas 데이터를 분석 중입니다...")
    courses = list(canvas.get_courses(include=['term'], enrollment_state=['active', 'completed']))
    term_map = defaultdict(list)

    for course in courses:
        if not hasattr(course, 'name'): continue
        term_name = course.term['name'] if hasattr(course, 'term') else "기타"
        term_map[term_name].append(course)

    terms = sorted(term_map.keys(), reverse=True)
    print("\n=== [1단계: 학기 선택] ===")
    for i, term in enumerate(terms):
        print(f"[{i+1}] {term}")

    term_choice = int(input("\n번호 입력: ")) - 1
    selected_term_name = terms[term_choice]
    courses_in_term = term_map[selected_term_name]

    print(f"\n=== [2단계: {selected_term_name} 과목 선택] ===")
    for i, course in enumerate(courses_in_term):
        print(f"[{i+1}] {course.name}")

    user_input = input("\n💡 백업할 번호(1,2 / all): ").strip()
    final_selected = courses_in_term if user_input.lower() == 'all' else [courses_in_term[int(x)-1] for x in user_input.split(',')]

    # 메일 발송용 요약 데이터
    backup_summary = defaultdict(list)

    # 3. 백업 실행
    for course in final_selected:
        print(f"\n📂 [{course.name}] 동기화 중...")
        course_folder_id = get_or_create_folder(drive_service, course.name, root_folder_id)

        try:
            files = course.get_files()
            for file in files:
                safe_name = "".join([c for c in file.display_name if c not in r'<>:"/\|?*']).strip()

                query = f"name = '{safe_name}' and '{course_folder_id}' in parents and trashed = false"
                existing = drive_service.files().list(q=query, supportsAllDrives=True).execute().get('files', [])

                if existing:
                    continue

                print(f"  📥 업로드: {safe_name}")
                res = requests.get(file.url, headers={'Authorization': f'Bearer {CANVAS_API_KEY}'})

                file_mime = getattr(file, 'mime_type', 'application/octet-stream')
                fh = io.BytesIO(res.content)
                media = MediaIoBaseUpload(fh, mimetype=file_mime, resumable=True)

                meta = {'name': safe_name, 'parents': [course_folder_id]}
                drive_service.files().create(body=meta, media_body=media, supportsAllDrives=True).execute()

                backup_summary[course.name].append(safe_name)
        except Exception as e:
            print(f"  ❌ 오류: {e}")

    # 4. 결과 메일 전송 (SMTP 방식 호출)
    print("\n" + "="*30)
    send_summary_email_smtp(backup_summary)
    print("✨ 모든 작업이 완료되었습니다!")

if __name__ == "__main__":
    main()