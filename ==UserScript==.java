// ==UserScript==
// @name         AutoSpeexx
// @namespace    http://tampermonkey.net/
// @version      1.0
// @description  ทำงานอัตโนมัติบนแพลตฟอร์ม Speexx ทั้งเล่นวิดีโอ, ทำแบบฝึกหัด, ข้ามแบบฝึกหัดการออกเสียง, ข้ามหน้าอัตโนมัติ พร้อมตั้งค่าเวลาจำลองการทำโจทย์เสมือนจริงได้
// @author       0x9c5
// @match        https://portal.speexx.cn/articles/*
// @match        https://portal.speexx.com/articles/*
// @grant        GM_addStyle
// @run-at       document-start
// @license      GPL-3.0-only
// @downloadURL https://update.greasyfork.org/scripts/571011/AutoSpeexx.user.js
// @updateURL https://update.greasyfork.org/scripts/571011/AutoSpeexx.meta.js
// ==/UserScript==

(function() {
	'use strict';

	// -------------------------- 1. 核心配置 --------------------------
	const MIN_CONFIG = {
		MIN_TOTAL_TIME: 30000, // ค่าต่ำสุดของเวลารวมต่อข้อ (30 วินาที)
		MIN_RANDOM_DELAY: 1000 // ค่าต่ำสุดของความหน่วงสุ่ม (1000ms)
	};

	const DEFAULT_CONFIG = {
        // 基本配置
		MIN_TOTAL_TIME: 30000, // เวลาขั้นต่ำในการทำเสร็จต่อข้อ (ค่าเริ่มต้น 30 วินาที)
        PRON_WAIT: false, // เพิ่มเวลาพักในหน้าออกเสียงหรือไม่
		// 高级配置
		SOLVE_DELAY: 10000, // หน่วงเวลาก่อนแสดงเฉลย
		SUBMIT_DELAY: 10000, // หน่วงเวลาหลังกดส่ง
		NEXT_LOAD_DELAY: 3000, // หน่วงเวลาโหลดหน้าถัดไป
		SCORE_CHECK_DELAY: 2000, // หน่วงเวลาตรวจสอบคะแนน
        MAX_RANDOM_DELAY: 5000, // ค่าสูงสุดของความหน่วงสุ่ม (ค่าเริ่มต้น 5 วินาที)
	};
	let CONFIG = {
		...DEFAULT_CONFIG
	};

	// -------------------------- 2. 前端样式 --------------------------
	GM_addStyle(`
        /* 主面板 */
        .autospeexx-panel {
            max-height: 650px;
            display: flex;
            flex-direction: column;
            overflow: hidden;
            position: fixed;
            top: 20px;
            right: 20px;
            width: 400px;
            background: #fff !important;
            border: 1px solid #ccc;
            border-radius: 8px;
            padding: 12px;
            box-shadow: 0 2px 15px rgba(0,0,0,0.2);
            z-index: 999999;
            font-family: Arial, sans-serif;
            font-size: 14px;
            color: #000 !important;
        }
        .autospeexx-panel, .autospeexx-panel * {
            box-sizing: border-box;
        }

        /* 表头&拖动 */
        .autospeexx-header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            font-weight: bold;
            margin-bottom: 10px;
            padding-bottom: 8px;
            border-bottom: 1px solid #eee;
            cursor: move;
            user-select: none;
        }
        .autospeexx-title { font-size: 16px; }
        .autospeexx-min-btn {
            background: none;
            border: none;
            font-size: 18px;
            cursor: pointer;
            color: #666 !important;
            padding: 0 5px;
        }

        /* 分页 */
        .autospeexx-tabs {
            display: flex;
            margin-bottom: 10px;
            border-bottom: 1px solid #eee;
            padding-bottom: 8px;
        }
        .autospeexx-tab {
            padding: 6px 12px;
            cursor: pointer;
            border-radius: 4px;
            margin-right: 8px;
            color: #000 !important;
        }
        .autospeexx-tab.active {
            background: #ff7700 !important;
            color: #fff !important;
        }
        .autospeexx-tab:not(.active):hover {
            background: #f5f5f5 !important;
        }
        .autospeexx-tab-content {
            display: none;
            color: #000 !important;
        }
        .autospeexx-tab-content.active {
            display: block;
        }

        /* 悬浮球（左下角） */
        .autospeexx-float-ball {
            position: fixed;
            bottom: 20px;
            left: 20px;
            width: 60px;
            height: 60px;
            background: #ff7700 !important;
            border-radius: 50%;
            color: #fff !important;
            display: none;
            justify-content: center;
            align-items: center;
            cursor: pointer;
            z-index: 999999;
        }
        .autospeexx-float-ball::before {
            content: "🚀";
            font-size: 30px;
        }

        /* 按钮 */
        .autospeexx-btn {
            padding: 6px 12px;
            margin: 5px 0;
            border: none;
            border-radius: 4px;
            cursor: pointer;
            width: 100%;
        }
        .autospeexx-btn-start { background: #28a745 !important; color: #fff !important; }
        .autospeexx-btn-start:disabled { background: #6c757d !important; }
        .autospeexx-btn-stop { background: #dc3545 !important; color: #fff !important; }
        .autospeexx-btn-stop:disabled { background: #6c757d !important; }
        .autospeexx-btn-save { background: #007bff !important; color: #fff !important; margin-right: 5px; }
        .autospeexx-btn-reset { background: #6c757d !important; color: #fff !important; }

        /* 参数配置 */
        .autospeexx-setting-item {
            margin: 10px 0;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }
        .autospeexx-setting-label {
            font-size: 13px;
            color: #333 !important;
            width: 180px;
        }
        .autospeexx-setting-input {
            width: 100px;
            padding: 4px 8px;
            border: 1px solid #ccc;
            border-radius: 4px;
            font-size: 13px;
        }

        /* 中间滚动容器 */
        .autospeexx-scroll-container {
            flex-grow: 1;
            overflow-y: scroll;
            padding: 6px;
            border-bottom: 1px solid #eee;
        }

        .autospeexx-scroll-container::-webkit-scrollbar {
            width: 6px;
        }
        .autospeexx-scroll-container::-webkit-scrollbar-thumb {
            background: #ddd;
            border-radius: 10px;
        }

        .autospeexx-advanced-settings summary {
            list-style: none !important;
            outline: none !important;
            cursor: pointer;
            position: relative;
            padding-left: 20px !important;
            user-select: none;
            color: #666;
            font-size: 13px;
            margin: 10px 0;
        }
        .autospeexx-advanced-settings summary::-webkit-details-marker {
            display: none !important;
        }
        .autospeexx-advanced-settings summary::before {
            content: '▶';
            position: absolute;
            left: 0;
            top: 50%;
            transform: translateY(-50%);
            font-size: 10px;
            color: #dc3545;
            transition: transform 0.2s ease;
        }
        .autospeexx-advanced-settings[open] summary::before {
            transform: translateY(-50%) rotate(90deg);
        }

        /* 状态 & 日志 */
        .autospeexx-page-type { font-size: 12px; color: #007bff !important; margin: 5px 0; }
        .autospeexx-tip { font-size: 12px; color:rgb(128, 128, 128) !important; margin: 5px 0; }
        .autospeexx-log {
            flex-shrink: 0;
            height: 160px;
            border: 1px solid #eee;
            padding: 8px;
            margin-top: 10px;
            font-size: 12px;
            overflow-y: auto;
            background: #f0f0f0 !important;
            white-space: pre-wrap;
        }

        .autospeexx-panel.minimized { display: none !important; }
    `);

	// -------------------------- 3. 全局变量 --------------------------
	let isRunning = false;
	let isMinimized = false;
	let currentTaskTimer = null;
	let currentPageType = 'unknown';
	let logList = [];
	const STORAGE_KEY = 'AutoSpeexxRunning';
	const CONFIG_STORAGE_KEY = 'AutoSpeexxConfig';

	// -------------------------- 4. 工具函数 --------------------------
	function addLog(msg) {
		const time = new Date().toLocaleTimeString();
		const logItem = `[${time}] ${msg}`;
		logList.unshift(logItem);
		if (logList.length > 20) logList.pop();
		const logDoms = document.querySelectorAll('.autospeexx-log');
		logDoms.forEach(dom => {
			dom.innerHTML = logList.join('<br>');
		});
		console.log(`[Speexx] ${logItem}`);
	}

	function loadConfig() {
		const savedConfig = localStorage.getItem(CONFIG_STORAGE_KEY);
		if (savedConfig) {
			try {
				const parsed = JSON.parse(savedConfig);
				CONFIG.MIN_TOTAL_TIME = Math.max(parsed.MIN_TOTAL_TIME || DEFAULT_CONFIG.MIN_TOTAL_TIME, MIN_CONFIG.MIN_TOTAL_TIME);
                if (typeof parsed.PRON_WAIT === 'boolean') {CONFIG.PRON_WAIT = parsed.PRON_WAIT;} else {CONFIG.PRON_WAIT = DEFAULT_CONFIG.PRON_WAIT;}

				CONFIG.SOLVE_DELAY = (typeof parsed.SOLVE_DELAY === 'number' && parsed.SOLVE_DELAY > 0) ? parsed.SOLVE_DELAY : DEFAULT_CONFIG.SOLVE_DELAY;
				CONFIG.SUBMIT_DELAY = (typeof parsed.SUBMIT_DELAY === 'number' && parsed.SUBMIT_DELAY > 0) ? parsed.SUBMIT_DELAY : DEFAULT_CONFIG.SUBMIT_DELAY;
				CONFIG.NEXT_LOAD_DELAY = (typeof parsed.NEXT_LOAD_DELAY === 'number' && parsed.NEXT_LOAD_DELAY > 0) ? parsed.NEXT_LOAD_DELAY : DEFAULT_CONFIG.NEXT_LOAD_DELAY;
				CONFIG.SCORE_CHECK_DELAY = (typeof parsed.SCORE_CHECK_DELAY === 'number' && parsed.SCORE_CHECK_DELAY > 0) ? parsed.SCORE_CHECK_DELAY : DEFAULT_CONFIG.SCORE_CHECK_DELAY;
                CONFIG.MAX_RANDOM_DELAY = Math.max(parsed.MAX_RANDOM_DELAY || DEFAULT_CONFIG.MAX_RANDOM_DELAY, MIN_CONFIG.MIN_RANDOM_DELAY);

				addLog('✅ โหลดการตั้งค่าสำเร็จ');
			} catch (e) {
				addLog(`❌ โหลดการตั้งค่าไม่สำเร็จ: ${e.message}`);
			}
		}
		updateConfigInputs();
	}

	function saveConfig() {
		const minTotalTime = Math.max(Number(document.getElementById('minTotalTimeInput').value) || DEFAULT_CONFIG.MIN_TOTAL_TIME, MIN_CONFIG.MIN_TOTAL_TIME);
        const pronWait = document.getElementById('pronWaitInput') ? document.getElementById('pronWaitInput').checked : DEFAULT_CONFIG.PRON_WAIT;

		const solveDelay = (Number(document.getElementById('solveDelayInput').value) > 0) ? Number(document.getElementById('solveDelayInput').value) : DEFAULT_CONFIG.SOLVE_DELAY;
		const submitDelay = (Number(document.getElementById('submitDelayInput').value) > 0) ? Number(document.getElementById('submitDelayInput').value) : DEFAULT_CONFIG.SUBMIT_DELAY;
		const nextLoadDelay = (Number(document.getElementById('nextLoadDelayInput').value) > 0) ? Number(document.getElementById('nextLoadDelayInput').value) : DEFAULT_CONFIG.NEXT_LOAD_DELAY;
		const scoreCheckDelay = (Number(document.getElementById('scoreCheckDelayInput').value) > 0) ? Number(document.getElementById('scoreCheckDelayInput').value) : DEFAULT_CONFIG.SCORE_CHECK_DELAY;
        const maxRandomDelay = Math.max(Number(document.getElementById('maxRandomDelayInput').value) || DEFAULT_CONFIG.MAX_RANDOM_DELAY, MIN_CONFIG.MIN_RANDOM_DELAY);

		CONFIG.MIN_TOTAL_TIME = minTotalTime;
        CONFIG.PRON_WAIT = pronWait;
		CONFIG.SOLVE_DELAY = solveDelay;
		CONFIG.SUBMIT_DELAY = submitDelay;
		CONFIG.NEXT_LOAD_DELAY = nextLoadDelay;
		CONFIG.SCORE_CHECK_DELAY = scoreCheckDelay;
        CONFIG.MAX_RANDOM_DELAY = maxRandomDelay;

		localStorage.setItem(CONFIG_STORAGE_KEY, JSON.stringify({
			MIN_TOTAL_TIME: CONFIG.MIN_TOTAL_TIME,
            PRON_WAIT: CONFIG.PRON_WAIT,
			SOLVE_DELAY: CONFIG.SOLVE_DELAY,
			SUBMIT_DELAY: CONFIG.SUBMIT_DELAY,
			NEXT_LOAD_DELAY: CONFIG.NEXT_LOAD_DELAY,
			SCORE_CHECK_DELAY: CONFIG.SCORE_CHECK_DELAY,
            MAX_RANDOM_DELAY: CONFIG.MAX_RANDOM_DELAY
		}));

		updateConfigInputs();
		addLog(`⚙️ บันทึกการตั้งค่าสำเร็จ:
ตั้งค่าพื้นฐาน: เวลาขั้นต่ำต่อข้อ ${CONFIG.MIN_TOTAL_TIME/1000} วินาที, หน่วงสุ่มสูงสุด ${CONFIG.MAX_RANDOM_DELAY/1000} วินาที, พักหน้าออกเสียงนานขึ้น [${CONFIG.PRON_WAIT ? 'เปิด' : 'ปิด'}]
ตั้งค่าขั้นสูง: หน่วงแสดงเฉลย ${CONFIG.SOLVE_DELAY/1000} วินาที, หน่วงส่ง ${CONFIG.SUBMIT_DELAY/1000} วินาที, หน่วงโหลดข้ามหน้า ${CONFIG.NEXT_LOAD_DELAY/1000} วินาที, หน่วงตรวจคะแนน ${CONFIG.SCORE_CHECK_DELAY/1000} วินาที`);
	}

	function resetConfig() {
		CONFIG.MIN_TOTAL_TIME = DEFAULT_CONFIG.MIN_TOTAL_TIME;
        CONFIG.PRON_WAIT = DEFAULT_CONFIG.PRON_WAIT;
		CONFIG.SOLVE_DELAY = DEFAULT_CONFIG.SOLVE_DELAY;
		CONFIG.SUBMIT_DELAY = DEFAULT_CONFIG.SUBMIT_DELAY;
		CONFIG.NEXT_LOAD_DELAY = DEFAULT_CONFIG.NEXT_LOAD_DELAY;
		CONFIG.SCORE_CHECK_DELAY = DEFAULT_CONFIG.SCORE_CHECK_DELAY;
        CONFIG.MAX_RANDOM_DELAY = DEFAULT_CONFIG.MAX_RANDOM_DELAY;

		localStorage.setItem(CONFIG_STORAGE_KEY, JSON.stringify({
			MIN_TOTAL_TIME: CONFIG.MIN_TOTAL_TIME,
            PRON_WAIT: CONFIG.PRON_WAIT,
			SOLVE_DELAY: CONFIG.SOLVE_DELAY,
			SUBMIT_DELAY: CONFIG.SUBMIT_DELAY,
			NEXT_LOAD_DELAY: CONFIG.NEXT_LOAD_DELAY,
			SCORE_CHECK_DELAY: CONFIG.SCORE_CHECK_DELAY,
            MAX_RANDOM_DELAY: CONFIG.MAX_RANDOM_DELAY
		}));

		updateConfigInputs();
		addLog('⚙️ รีเซ็ตการตั้งค่าเป็นค่าเริ่มต้นแล้ว');
	}

	function updateConfigInputs() {
		const minTotalTimeInput = document.getElementById('minTotalTimeInput');
        const pronWaitInput = document.getElementById('pronWaitInput');

		if (minTotalTimeInput) minTotalTimeInput.value = CONFIG.MIN_TOTAL_TIME;
        pronWaitInput.checked = !!CONFIG.PRON_WAIT;

		const solveDelayInput = document.getElementById('solveDelayInput');
		const submitDelayInput = document.getElementById('submitDelayInput');
		const nextLoadDelayInput = document.getElementById('nextLoadDelayInput');
		const scoreCheckDelayInput = document.getElementById('scoreCheckDelayInput');
        const maxRandomDelayInput = document.getElementById('maxRandomDelayInput');

		if (solveDelayInput) solveDelayInput.value = CONFIG.SOLVE_DELAY;
		if (submitDelayInput) submitDelayInput.value = CONFIG.SUBMIT_DELAY;
		if (nextLoadDelayInput) nextLoadDelayInput.value = CONFIG.NEXT_LOAD_DELAY;
		if (scoreCheckDelayInput) scoreCheckDelayInput.value = CONFIG.SCORE_CHECK_DELAY;
        if (maxRandomDelayInput) maxRandomDelayInput.value = CONFIG.MAX_RANDOM_DELAY;
	}

	function getRandomDelay() {
		return Math.floor(Math.random() * (CONFIG.MAX_RANDOM_DELAY - MIN_CONFIG.MIN_RANDOM_DELAY)) + MIN_CONFIG.MIN_RANDOM_DELAY;
	}

	function isResultPage() {
		return !!document.querySelector('.graphic-stats');
	}

	function detectPageType() {
		const url = window.location.href;
		let type = 'unknown';

		if (document.querySelector('.graphic-stats')) {
			type = 'result';
		} else if (document.querySelector('.microphone-pulse') || url.includes('/pronunciation')) {
			type = 'pronunciation';
		} else if (document.querySelector('.vjs-big-play-button') || url.includes('/video')) {
			type = 'video';
		} else if (window.location.href.includes('/exercise') || document.querySelector('.exercise-content')) {
			type = 'exercise';
		} else if (document.querySelector('[data-testid="daily-practice-container"]') || document.querySelector('[aria-label="主页"]')) {
			type = 'home';
		} else if (url.includes('/magazine') || url.includes('/vocabulary')) {
			type = 'unknown';
		}

		if (type !== currentPageType) {
			addLog(`🔄 ชนิดของหน้า: ${currentPageType} → ${type}`);
			currentPageType = type;
			const pageTypeDoms = document.querySelectorAll('.autospeexx-page-type');
			pageTypeDoms.forEach(dom => {
				dom.textContent = `หน้าปัจจุบัน: ${type}`;
			});
		}
		return type;
	}

	function isExerciseLoaded() {
		return !!document.querySelector('.exercise-content') || !!document.querySelector('.question');
	}

	function hasNextQuestion() {
		const nextBtn = document.querySelector('.next');
		return nextBtn && !nextBtn.disabled;
	}

	function isScoreDisplayed() {
		return !!document.querySelector('span[class*="result"]') || !!document.querySelector('span[class*="result-badge-container"]') || !!document.querySelector('.next:not(:disabled)');
	}

	function initExerciseComponent() {
		try {
			if (!window.entryp) {
				window.entryp = {
					trigger: function(action) {
						if (currentPageType === 'video' && action === 'correct') {
							addLog('⚠️ ข้ามการทริกเกอร์ correction ในหน้าวิดีโอ (ป้องกัน JS พลาด)');
							return;
						}
						const btn = document.querySelector(`.btn-${action}`) || document.querySelector(`[data-action="${action}"]`);
						if (btn) btn.click();
					}
				};
			}

			CourseWare.CourseExercises.CourseExercisesControlsView = Backbone.Speexx.HandlebarsView.extend({
				templateName: "cw-language-course-controls",
				className: "exercise-controls",
				initialize: function(e) {
					var t = this.exerciseView = e.exerciseView,
						n = t.model;
					this.firstTime = !0;
					this.solutionShown = !1;
					this.modified = !1;
					this.dialogEnded = !1;

					this.listenTo(t, "modified", () => {
						this.modified || (this.modified = !0, this.render());
					});
					this.listenToOnce(t, "dialog:ended", () => {
						this.dialogEnded = !0, this.render();
					});
					this.listenTo(t, "static:added static:removed", () => {
						this.render();
					});
					this.listenTo(n, "sync", (e, t, n) => {
						var r = !!this.silent;
						this.silent = !!n.silent;
						(!this.silent || !r) && this.render();
					});
					this.listenTo(CourseWare.Language, "change:textpool", () => {
						this.render();
					});
					this.on("render:after", () => {
						this.$(".btn.btn-link[title]").tooltip();
					});

					window.entryp = this;
				},
				templateModel: function() {
					var e = this.exerciseView,
						t = e.model,
						n = this.silent ? null : t.get("result");
					var r = t.get("type").pronunciation,
						i = !e.static && t.get("type").hasResult && !r;
					var s = e.static || !t.get("type").hasResult,
						o = !this.solutionShown && i && !this.firstTime && n !== 100;
					return {
						firstTime: this.firstTime,
						hasCorrect: i,
						hasSolution: o,
						hideResult: s,
						dialogEnded: this.dialogEnded,
						modified: this.modified,
						solutionShown: this.solutionShown,
						result: n
					};
				},
				events: {
					"click .btn.correct": function() {
						CourseWare.Audio.stop();
						this.trigger("correct");
					},
					"click .btn.next": function() {
						this.trigger("next");
					},
					"click .btn.solution": function() {
						this.render();
						this.trigger("solve");
					}
				}
			});

			addLog('✅ เริ่มต้นคอมโพเนนต์แบบฝึกหัดสำเร็จ');
		} catch (e) {
			addLog(`⚠️ เกิดข้อผิดพลาดในการเริ่มต้นคอมโพเนนต์: ${e.message}`);
		}
	}

	// -------------------------- 5. 核心处理逻辑 --------------------------
	function processCurrentPage() {
		if (!isRunning) return;

		const pageType = detectPageType();

		switch (pageType) {
			case 'result':
				processResultPage();
				break;
			case 'pronunciation':
				processPronunciationPage();
				break;
			case 'video':
				processVideoPage();
				break;
			case 'exercise':
				processExercisePage();
				break;
			case 'home':
			case 'unknown':
				addLog('❌ อยู่ในหน้าแรกหรือหน้าอื่น, หยุดทำงานอัตโนมัติ');
				stopTask();
				break;
		}
	}

	function processResultPage() {
		addLog('✅ ตรวจพบหน้าสรุปผล, กำลังหน่วงเวลาแบบสุ่มก่อนกด Next');
		const randomDelay = getRandomDelay();
		addLog(`⌛ หน่วงเวลาในหน้าสรุปผล ${randomDelay/1000} วินาที`);

		setTimeout(() => {
			if (!isRunning) return;

			const nextBtn = document.querySelector('.next');
			if (nextBtn && !nextBtn.disabled) {
				nextBtn.click();
				addLog('✅ กด Next ในหน้าสรุปผลแล้ว, ไปยังหน้าถัดไป');

				if (currentTaskTimer) {
					clearTimeout(currentTaskTimer);
					currentTaskTimer = null;
				}
				addLog(`⌛ รอโหลดหน้าใหม่ ${CONFIG.NEXT_LOAD_DELAY/1000} วินาที`);
				currentTaskTimer = setTimeout(() => {
					if (isRunning) {
						processCurrentPage();
					}
				}, CONFIG.NEXT_LOAD_DELAY);
			} else {
				addLog('⚠️ ไม่พบปุ่ม Next ที่ใช้งานได้ในหน้าสรุปผล, งานเสร็จสิ้น');
				stopTask();
			}
		}, randomDelay);
	}

	function processPronunciationPage() {
		addLog('✅ ตรวจพบหน้าฝึกออกเสียง, เปิดใช้งานโหมดข้ามอัตโนมัติ');

		if (!hasNextQuestion()) {
			addLog('⌛ ปุ่ม Next ของหน้าออกเสียงยังไม่พร้อม, ลองใหม่ใน 2 วินาที...');
			currentTaskTimer = setTimeout(processPronunciationPage, 2000);
			return;
		}

		const randomDelay = getRandomDelay();
		addLog(`⌛ หน่วงเวลา ${randomDelay * (3 ** CONFIG.PRON_WAIT)/1000} วินาทีก่อนกด Next`);

		setTimeout(() => {
			if (!isRunning) return;
			const nextBtn = document.querySelector('.next');
			if (nextBtn && !nextBtn.disabled) {
				nextBtn.click();
				addLog('✅ กด Next สำเร็จ, ไปยังหน้าถัดไป');
				setTimeout(processCurrentPage, CONFIG.NEXT_LOAD_DELAY);
			} else {
				addLog('⚠️ ปุ่ม Next ในหน้าออกเสียงไม่พร้อมใช้งาน, หยุดทำงาน');
				stopTask();
			}
		}, randomDelay * (3 ** CONFIG.PRON_WAIT));
	}

	function processVideoPage() {
		addLog('✅ ตรวจพบหน้าวิดีโอ, เริ่มต้นระบบเล่นวิดีโออัตโนมัติ');

		const playBtn = document.querySelector('.vjs-big-play-button') || document.querySelector('.video-play-btn');
		if (playBtn) {
			playBtn.click();
			addLog('▶️ กำลังเล่นวิดีโอ');
		} else {
			addLog('⚠️ ไม่טיปุ่มเล่น, สมมติว่าวิดีโอกำลังเล่นอยู่');
		}

		const checkVideoComplete = () => {
			if (!isRunning) return;

			let isComplete = false;
			const videoElement = document.querySelector('video');
			const timeDisplay = document.querySelector('.vjs-remaining-time-display');

			if (timeDisplay && timeDisplay.textContent.trim() === '0:00') {
				isComplete = true;
			} else if (videoElement && videoElement.ended) {
				isComplete = true;
			} else if (document.querySelector('.video-complete') || document.querySelector('.lesson-complete')) {
				isComplete = true;
			}

			if (isComplete) {
				addLog('✅ วิดีโอเล่นจบแล้ว');
				const randomDelay = getRandomDelay();
				addLog(`✅ หน่วงเวลาหลังวิดีโอจบ ${randomDelay/1000} วินาที`);

				setTimeout(() => {
					if (!isRunning) return;

					const nextBtn = document.querySelector('.next');
					if (nextBtn && !nextBtn.disabled) {
						nextBtn.click();
						addLog('✅ กด Next ในหน้าวิดีโอแล้ว, ไปยังหน้าถัดไป');
						setTimeout(processCurrentPage, CONFIG.NEXT_LOAD_DELAY);
					} else {
						addLog('⚠️ ไม่พบปุ่ม Next ในหน้าวิดีโอ, สิ้นสุดภารกิจ');
						stopTask();
					}
				}, randomDelay);
			} else {
				currentTaskTimer = setTimeout(checkVideoComplete, 1000);
			}
		};

		checkVideoComplete();
	}

	function processExercisePage() {
		if (isResultPage()) {
			processResultPage();
			return;
		}

		const startBtn = document.querySelector('.btn-primary.start-exercise');
		if (startBtn) {
			addLog('🔎 พบปุ่ม Start, กำลังพยายามเริ่มแบบฝึกหัด...');
			startBtn.click();
			currentTaskTimer = setTimeout(processCurrentPage, 1500);
			return;
		}

		if (!isExerciseLoaded()) {
			addLog('⚠️ แบบฝึกหัดยังไม่โหลด, ลองใหม่ใน 3 วินาที');
			initExerciseComponent();
			currentTaskTimer = setTimeout(processExercisePage, 3000);
			return;
		}

		const startTime = Date.now();
		addLog(`✅ เริ่มทำแบบฝึกหัด (เวลาขั้นต่ำ ${CONFIG.MIN_TOTAL_TIME/1000} วินาที)`);

		if (window.entryp) {
			window.entryp.trigger("solve");
			addLog(`✅ แสดงเฉลยแล้ว, รอ ${CONFIG.SOLVE_DELAY/1000} วินาที`);
		}

		setTimeout(() => {
			if (!isRunning) return;

			if (window.entryp) {
				window.entryp.trigger("correct");
				addLog(`✅ ส่งคำตอบแล้ว, รอ ${CONFIG.SUBMIT_DELAY/1000} วินาที`);
			}

			setTimeout(() => {
				if (!isRunning) return;

				addLog('✅ กำลังตรวจสอบการแสดงผลคะแนน');
				if (!isScoreDisplayed()) {
					addLog(`⚠️ ไม่พบการแสดงคะแนน, รอเพิ่มอีก ${CONFIG.SCORE_CHECK_DELAY/1000} วินาที`);
					setTimeout(proceedToNext, CONFIG.SCORE_CHECK_DELAY);
				} else {
					proceedToNext();
				}

				function proceedToNext() {
					const elapsedTime = Date.now() - startTime;
					const needWaitTime = Math.max(0, CONFIG.MIN_TOTAL_TIME - elapsedTime);

					if (needWaitTime > 0) {
						addLog(`⌛ รอเวลาเพิ่มเติม ${needWaitTime/1000} วินาที (เพื่อให้ครบเวลาขั้นต่ำต่อข้อ)`);
						setTimeout(() => {
							addRandomDelayThenNext();
						}, needWaitTime);
					} else {
						addRandomDelayThenNext();
					}
				}

				function addRandomDelayThenNext() {
					const randomDelay = getRandomDelay();
					addLog(`⌛ หน่วงเวลาแบบสุ่ม ${randomDelay/1000} วินาที`);
					setTimeout(() => {
						clickNext();
					}, randomDelay);
				}

				function clickNext() {
					if (!isRunning) return;

					if (isResultPage()) {
						processResultPage();
						return;
					}

					if (hasNextQuestion()) {
						const nextBtn = document.querySelector('.next');
						nextBtn.click();
						addLog('✅ กด Next ไปยังข้อถัดไป');
						setTimeout(processCurrentPage, CONFIG.NEXT_LOAD_DELAY);
					} else {
						addLog('✅ ไม่มีข้อถัดไปแล้ว, งานเสร็จสมบูรณ์');
						stopTask();
					}
				}

			}, CONFIG.SUBMIT_DELAY);

		}, CONFIG.SOLVE_DELAY);
	}

	// -------------------------- 6. 启动/停止任务 --------------------------
	function startTask() {
		if (isRunning) return;

		detectPageType();
		initExerciseComponent();

		if (['video', 'exercise', 'result', 'pronunciation'].indexOf(currentPageType) === -1) {
			addLog('❌ อยู่ในหน้าหลักหรือหน้าอื่น, ไม่สามารถเริ่มทำงานได้');
			return;
		}

		isRunning = true;
		localStorage.setItem(STORAGE_KEY, 'true');
		document.getElementById('startBtn').disabled = true;
		document.getElementById('stopBtn').disabled = false;
		addLog('===== เริ่มต้นทำงานอัตโนมัติ =====');

		processCurrentPage();
	}

	function stopTask() {
		if (currentTaskTimer) clearTimeout(currentTaskTimer);
		currentTaskTimer = null;

		isRunning = false;
		localStorage.removeItem(STORAGE_KEY);
		document.getElementById('startBtn').disabled = false;
		document.getElementById('stopBtn').disabled = true;
		addLog('===== หยุดทำงานอัตโนมัติ =====');
	}

	// -------------------------- 7. 面板初始化 --------------------------
	function makeDraggable(dragTarget, dragHandle) {
		let isDragging = false;
		let offsetX, offsetY;

		dragHandle.addEventListener('mousedown', startDrag);
		document.addEventListener('mousemove', drag);
		document.addEventListener('mouseup', stopDrag);

		function startDrag(e) {
			e.preventDefault();
			isDragging = true;
			const rect = dragTarget.getBoundingClientRect();
			offsetX = e.clientX - rect.left;
			offsetY = e.clientY - rect.top;
			dragTarget.style.zIndex = 999999;
		}

		function drag(e) {
			if (!isDragging) return;
			e.preventDefault();

			let newX = e.clientX - offsetX;
			let newY = e.clientY - offsetY;

			const windowWidth = window.innerWidth;
			const windowHeight = window.innerHeight;
			const panelWidth = dragTarget.offsetWidth;
			const panelHeight = dragTarget.offsetHeight;

			newX = Math.max(0, Math.min(newX, windowWidth - panelWidth));
			newY = Math.max(0, Math.min(newY, windowHeight - panelHeight));

			dragTarget.style.left = `${newX}px`;
			dragTarget.style.top = `${newY}px`;
			dragTarget.style.right = 'auto';
			dragTarget.style.bottom = 'auto';
		}

		function stopDrag() {
			isDragging = false;
		}

		dragTarget.addEventListener('remove', () => {
			dragHandle.removeEventListener('mousedown', startDrag);
			document.removeEventListener('mousemove', drag);
			document.removeEventListener('mouseup', stopDrag);
		});
	}

	function initPanel() {
		const panel = document.createElement('div');
		panel.className = 'autospeexx-panel';
		panel.innerHTML = `
            <div class="autospeexx-header">
                <div class="autospeexx-title">Auto<span style="color:#ff7700;">Speexx</span></div>
                <button class="autospeexx-min-btn">—</button>
            </div>

            <!-- 分页 -->
            <div class="autospeexx-tabs">
                <div class="autospeexx-tab active" data-tab="main">ฟังก์ชันหลัก</div>
                <div class="autospeexx-tab" data-tab="settings">ตั้งค่า</div>
            </div>

            <div class="autospeexx-scroll-container">
                <!-- 核心功能页 -->
                <div class="autospeexx-tab-content active" id="mainTab">
                    <button class="autospeexx-btn autospeexx-btn-start" id="startBtn">เริ่มทำงานอัตโนมัติ</button>
                    <button class="autospeexx-btn autospeexx-btn-stop" id="stopBtn" disabled>หยุดทำงานอัตโนมัติ</button>
                    <div class="autospeexx-page-type">หน้าปัจจุบัน: unknown</div>
                    <div class="autospeexx-tip">💡 รองรับเล่นวิดีโอออโต้ / ทำแบบฝึกหัดออโต้ / ข้ามฝึกออกเสียง</div>
                    <div class="autospeexx-tip">💡 หากพบปัญหา ให้ลองกลับหน้าก่อนหน้าแล้วกดเริ่มใหม่ หรือรีเฟรชหน้าเว็บ</div>
                </div>

                <!-- 参数配置页 -->
                <div class="autospeexx-tab-content" id="settingsTab">
                    <div style="font-weight:bold; margin:10px 0; color:#ff7700 !important;">⚙️ ตั้งค่าพื้นฐาน</div>
                    <div class="autospeexx-setting-item">
                        <label class="autospeexx-setting-label">เวลาขั้นต่ำต่อข้อ (ms):</label>
                        <input type="number" class="autospeexx-setting-input" id="minTotalTimeInput">
                    </div>
                    <div class="autospeexx-tip">💡 ควรกำหนดอย่างน้อย 30 วินาที เพื่อป้องกันชั่วโมงเรียนผิดปกติ</div>
                    <div class="autospeexx-setting-item">
                        <label class="autospeexx-setting-label">พักในหน้าออกเสียงนานขึ้น:</label>
                        <input type="checkbox" id="pronWaitInput" style="transform: scale(1.2);">
                    </div>
                    <div class="autospeexx-tip">💡 พักในหน้าออกเสียง 3 เท่า (หลีกเลี่ยงเวลาเรียน 0 นาที)</div>

                    <hr style="border:0; border-top:2px dashed #eee; margin:15px 0;">
                    <details class="autospeexx-advanced-settings">
                        <summary style="font-weight:bold; margin:10px 0; color:#dc3545 !important;">
                            🔩 ตั้งค่าขั้นสูง
                        </summary>
                        <div class="autospeexx-tip" style="color: red !important;">⚠️ หากไม่เข้าใจผลกระทบ แนะนำให้ใช้ค่าเริ่มต้น</div>
                        <div class="autospeexx-setting-item">
                            <label class="autospeexx-setting-label">หน่วงเวลาแสดงเฉลย (ms):</label>
                            <input type="number" class="autospeexx-setting-input" id="solveDelayInput">
                        </div>
                        <div class="autospeexx-tip">💡 เวลารอก่อนกด Solution</div>
                        <div class="autospeexx-setting-item">
                            <label class="autospeexx-setting-label">หน่วงเวลาส่งคำตอบ (ms):</label>
                            <input type="number" class="autospeexx-setting-input" id="submitDelayInput">
                        </div>
                        <div class="autospeexx-tip">💡 ดีเลย์หลังเรียกคำสั่งแก้โจทย์</div>
                        <div class="autospeexx-setting-item">
                            <label class="autospeexx-setting-label">หน่วงเวลาโหลดหน้าถัดไป (ms):</label>
                            <input type="number" class="autospeexx-setting-input" id="nextLoadDelayInput">
                        </div>
                        <div class="autospeexx-tip">💡 เวลารอสำรองหลังกด Next</div>
                        <div class="autospeexx-setting-item">
                            <label class="autospeexx-setting-label">หน่วงเวลาตรวจคะแนน (ms):</label>
                            <input type="number" class="autospeexx-setting-input" id="scoreCheckDelayInput">
                        </div>
                        <div class="autospeexx-tip">💡 ดีเลย์หลังจากพบผลลัพธ์คะแนน</div>
                        <div class="autospeexx-setting-item">
                            <label class="autospeexx-setting-label">สุ่มหน่วงเวลาสูงสุด (ms):</label>
                            <input type="number" class="autospeexx-setting-input" id="maxRandomDelayInput">
                        </div>
                        <div class="autospeexx-tip">💡 ช่วงสุ่มดีเลย์: 1000ms ถึงค่าสูงสุด (จำลองความเหมือนมนุษย์)</div>
                    </details>
                    <div style="margin-top:15px;">
                        <button class="autospeexx-btn autospeexx-btn-save" id="saveConfigBtn" style="width: auto;">บันทึก</button>
                        <button class="autospeexx-btn autospeexx-btn-reset" id="resetConfigBtn" style="width: auto;">ค่าเริ่มต้น</button>
                    </div>
                </div>
            </div>

            <div class="autospeexx-log">=== ประวัติการทำงาน ===</div>
        `;

		document.body.appendChild(panel);

		const floatBall = document.createElement('div');
		floatBall.className = 'autospeexx-float-ball';
		document.body.appendChild(floatBall);

		panel.querySelector('.autospeexx-min-btn').addEventListener('click', () => {
			isMinimized = true;
			panel.classList.add('minimized');
			floatBall.style.display = 'flex';
			addLog('🔽 ย่อแผงควบคุมลงมุมซ้ายล่างแล้ว');
		});
		floatBall.addEventListener('click', () => {
			isMinimized = false;
			panel.classList.remove('minimized');
			floatBall.style.display = 'none';
			addLog('🔼 แสดงแผงควบคุมขึ้นมาแล้ว');
		});

		const tabs = panel.querySelectorAll('.autospeexx-tab');
		const tabContents = panel.querySelectorAll('.autospeexx-tab-content');
		tabs.forEach(tab => {
			tab.addEventListener('click', () => {
				tabs.forEach(t => t.classList.remove('active'));
				tabContents.forEach(c => c.classList.remove('active'));
				tab.classList.add('active');
				const targetTab = document.getElementById(`${tab.dataset.tab}Tab`);
				if (targetTab) targetTab.classList.add('active');
			});
		});

		document.getElementById('startBtn').addEventListener('click', startTask);
		document.getElementById('stopBtn').addEventListener('click', stopTask);
		document.getElementById('saveConfigBtn').addEventListener('click', saveConfig);
		document.getElementById('resetConfigBtn').addEventListener('click', resetConfig);

		makeDraggable(panel, panel.querySelector('.autospeexx-header'));

		loadConfig();
		detectPageType();
		initExerciseComponent();

		if (localStorage.getItem(STORAGE_KEY) === 'true') {
			startTask();
		}

		setTimeout(() => {
			detectPageType();
			addLog('🔍 ตรวจสอบชนิดของหน้าซ้ำอีกครั้ง');
		}, 1500);
	}

	function waitForDOMReady() {
		if (document.readyState === 'complete' || document.readyState === 'interactive') {
			initPanel();
		} else {
			setTimeout(waitForDOMReady, 500);
		}
	}

	window.addEventListener('beforeunload', () => {
		if (currentTaskTimer) clearTimeout(currentTaskTimer);
		if (isRunning) localStorage.setItem(STORAGE_KEY, 'true');
	});

	waitForDOMReady();

})();
