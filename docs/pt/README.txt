2차 PT 제출용 PPT 만들기(웹 덱 site/pt 를 PPT 로)
1) cd site && python3 -m http.server 8765
2) node docs/pt/cap.cjs <작업폴더>      장마다 완성 장면 PNG(2배) + 등장 애니메이션 영상
3) 작업폴더에서 webm → mp4 로 자르고(meta.json 의 start), PNG → 2560 JPG, 첫 장면 _p0.jpg
4) python3 docs/pt/build_pptx.py <작업폴더> <출력.pptx>
PPT 한 장 = 바닥(첫 장면) + 자동 재생 영상 + 맨 위 완성 장면(영상이 끝나는 순간 나타남, 편집·인쇄에선 늘 보임).
글자는 모두 이미지라 글꼴이 깨지지 않는다. 발표자 메모 = index.html 의 data-note.
