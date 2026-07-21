package com.wemisson.career_camp.domain.admin.service.export;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.wemisson.career_camp.domain.admin.service.query.AdminRecruitmentQueryService;
import com.wemisson.career_camp.domain.participant.entity.ParticipantEntity;
import com.wemisson.career_camp.domain.participant.entity.ParticipantLectureEntity;
import com.wemisson.career_camp.domain.recruitment.dto.LectureType;
import com.wemisson.career_camp.domain.recruitment.entity.LectureEntity;
import com.wemisson.career_camp.domain.recruitment.entity.RecruitmentChurchEntity;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RecruitmentExcelExporter {

	private final AdminRecruitmentQueryService adminRecruitmentQueryService;

	@Transactional(readOnly = true)
	public ExportedRecruitmentExcel export(
		Long recruitmentId,
		String downloadType
	) throws IOException {
		AdminRecruitmentQueryService.ExcelSource source = adminRecruitmentQueryService.findExcelSource(
			recruitmentId,
			downloadType
		);
		return new ExportedRecruitmentExcel(
			source.recruitment().getName(),
			export(source)
		);
	}

	private byte[] export(AdminRecruitmentQueryService.ExcelSource source) throws IOException {
		try (XSSFWorkbook workbook = new XSSFWorkbook();
			 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
			CellStyle headerStyle = createHeaderStyle(workbook);

			if ("church".equals(source.downloadType())) {
				writeChurchParticipantSheets(
					workbook,
					headerStyle,
					source.churches(),
					source.participants(),
					source.lectureByParticipantId()
				);
			} else {
				writeLectureParticipantSheets(
					workbook,
					headerStyle,
					source.lectures(),
					source.applicationsByLectureId()
				);
			}

			workbook.write(outputStream);

			return outputStream.toByteArray();
		}
	}

	private CellStyle createHeaderStyle(XSSFWorkbook workbook) {
		Font font = workbook.createFont();
		font.setBold(true);

		CellStyle style = workbook.createCellStyle();
		style.setFont(font);

		return style;
	}

	private void writeLectureParticipantSheets(
		XSSFWorkbook workbook,
		CellStyle headerStyle,
		List<LectureEntity> lectures,
		Map<Long, List<ParticipantLectureEntity>> applicationsByLectureId
	) {
		Set<String> sheetNames = new HashSet<>();

		for (LectureEntity lecture : lectures) {
			List<ParticipantLectureEntity> applications = applicationsByLectureId.getOrDefault(
				lecture.getId(),
				List.of()
			);
			Sheet sheet = workbook.createSheet(uniqueSheetName(sheetNames, getLectureSheetPrefix(lecture) + "_" + lecture.getSpeakerName()));
			String[] headers = {"번호", "이름", "구분", "교회", "전화번호"};
			createHeaderRow(sheet, headerStyle, headers);

			for (int index = 0; index < applications.size(); index++) {
				ParticipantLectureEntity application = applications.get(index);
				ParticipantEntity participant = application.getParticipantEntity();
				Row row = sheet.createRow(index + 1);

				writeCell(row, 0, index + 1);
				writeCell(row, 1, participant.getName());
				writeCell(row, 2, getParticipantTypeName(participant));
				writeCell(row, 3, participant.getRecruitmentChurchEntity().getName());
				writeCell(row, 4, participant.getPhoneNumber());
			}

			autoSize(sheet, headers.length);
		}
	}

	private void writeChurchParticipantSheets(
		XSSFWorkbook workbook,
		CellStyle headerStyle,
		List<RecruitmentChurchEntity> churches,
		List<ParticipantEntity> participants,
		Map<Long, ParticipantLectureEntity> lectureByParticipantId
	) {
		Set<String> sheetNames = new HashSet<>();

		for (RecruitmentChurchEntity church : churches) {
			List<ParticipantEntity> churchParticipants = participants.stream()
				.filter(participant -> participant.getRecruitmentChurchEntity().getId().equals(church.getId()))
				.toList();
			Sheet sheet = workbook.createSheet(uniqueSheetName(sheetNames, church.getName()));
			String[] headers = {"번호", "이름", "구분", "전화번호", "오전 강사", "오후 강사"};
			createHeaderRow(sheet, headerStyle, headers);

			for (int index = 0; index < churchParticipants.size(); index++) {
				ParticipantEntity participant = churchParticipants.get(index);
				ParticipantLectureEntity participantLecture = lectureByParticipantId.get(participant.getId());
				Row row = sheet.createRow(index + 1);

				writeCell(row, 0, index + 1);
				writeCell(row, 1, participant.getName());
				writeCell(row, 2, getParticipantTypeName(participant));
				writeCell(row, 3, participant.getPhoneNumber());
				writeCell(row, 4, getLectureName(participantLecture == null ? null : participantLecture.getMorningLectureEntity()));
				writeCell(row, 5, getLectureName(participantLecture == null ? null : participantLecture.getAfternoonLectureEntity()));
			}

			autoSize(sheet, headers.length);
		}
	}

	private void createHeaderRow(
		Sheet sheet,
		CellStyle headerStyle,
		String[] headers
	) {
		Row row = sheet.createRow(0);

		for (int index = 0; index < headers.length; index++) {
			row.createCell(index).setCellValue(headers[index]);
			row.getCell(index).setCellStyle(headerStyle);
		}
	}

	private void writeCell(
		Row row,
		int index,
		String value
	) {
		row.createCell(index).setCellValue(value == null ? "" : value);
	}

	private void writeCell(
		Row row,
		int index,
		int value
	) {
		row.createCell(index).setCellValue(value);
	}

	private void autoSize(
		Sheet sheet,
		int columnCount
	) {
		for (int index = 0; index < columnCount; index++) {
			sheet.autoSizeColumn(index);
			sheet.setColumnWidth(index, Math.min(sheet.getColumnWidth(index) + 768, 14000));
		}
	}

	private String getParticipantTypeName(ParticipantEntity participant) {
		return participant.getType().isStudent() ? "학생" : "교사";
	}

	private String getLectureName(LectureEntity lectureEntity) {
		return lectureEntity == null ? "미신청" : lectureEntity.getSpeakerName();
	}

	private String getLectureSheetPrefix(LectureEntity lectureEntity) {
		return lectureEntity.getType() == LectureType.AM ? "오전" : "오후";
	}

	private String uniqueSheetName(
		Set<String> sheetNames,
		String requestedName
	) {
		String baseName = sanitizeSheetName(requestedName);
		String sheetName = truncateSheetName(baseName);
		int suffix = 2;

		while (sheetNames.contains(sheetName)) {
			String suffixText = "_" + suffix;
			sheetName = truncateSheetName(baseName, suffixText.length()) + suffixText;
			suffix++;
		}

		sheetNames.add(sheetName);

		return sheetName;
	}

	private String sanitizeSheetName(String sheetName) {
		String sanitized = sheetName.replaceAll("[\\\\/?*\\[\\]:]", "_").trim();

		if (sanitized.isBlank()) {
			return "Sheet";
		}

		return sanitized;
	}

	private String truncateSheetName(String sheetName) {
		return truncateSheetName(sheetName, 0);
	}

	private String truncateSheetName(
		String sheetName,
		int reservedLength
	) {
		int maxLength = 31 - reservedLength;

		if (sheetName.length() <= maxLength) {
			return sheetName;
		}

		return sheetName.substring(0, maxLength);
	}

	public record ExportedRecruitmentExcel(
		String recruitmentName,
		byte[] content
	) {
	}
}
