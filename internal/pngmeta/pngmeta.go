package pngmeta

import (
	"encoding/binary"
	"errors"
	"hash/crc32"
)

var pngHeader = []byte{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}

var crcTable = crc32.MakeTable(crc32.IEEE)

const keyword = "d2-source"

func Embed(pngBytes []byte, d2Source string) ([]byte, error) {
	if len(pngBytes) < 8 {
		return nil, errors.New("pngmeta: data too short")
	}
	for i, b := range pngHeader {
		if pngBytes[i] != b {
			return nil, errors.New("pngmeta: invalid PNG header")
		}
	}

	iendPos := -1
	pos := 8
	for pos+8 <= len(pngBytes) {
		chunkLen := int(binary.BigEndian.Uint32(pngBytes[pos : pos+4]))
		chunkType := string(pngBytes[pos+4 : pos+8])
		if chunkType == "IEND" {
			iendPos = pos
			break
		}
		pos += 8 + chunkLen + 4
	}
	if iendPos < 0 {
		return nil, errors.New("pngmeta: IEND chunk not found")
	}

	data := append([]byte(keyword), 0x00)
	data = append(data, []byte(d2Source)...)

	typeAndData := append([]byte("tEXt"), data...)
	crc := crc32.Checksum(typeAndData, crcTable)

	chunk := make([]byte, 4+4+len(data)+4)
	binary.BigEndian.PutUint32(chunk[0:4], uint32(len(data)))
	copy(chunk[4:8], "tEXt")
	copy(chunk[8:8+len(data)], data)
	binary.BigEndian.PutUint32(chunk[8+len(data):], crc)

	result := make([]byte, 0, len(pngBytes)+len(chunk))
	result = append(result, pngBytes[:iendPos]...)
	result = append(result, chunk...)
	result = append(result, pngBytes[iendPos:]...)
	return result, nil
}

func Extract(pngBytes []byte) (string, bool) {
	if len(pngBytes) < 8 {
		return "", false
	}
	for i, b := range pngHeader {
		if pngBytes[i] != b {
			return "", false
		}
	}

	prefix := keyword + "\x00"
	pos := 8
	for pos+8 <= len(pngBytes) {
		chunkLen := int(binary.BigEndian.Uint32(pngBytes[pos : pos+4]))
		chunkType := string(pngBytes[pos+4 : pos+8])
		dataStart := pos + 8
		dataEnd := dataStart + chunkLen
		if dataEnd > len(pngBytes) {
			break
		}
		if chunkType == "tEXt" {
			data := string(pngBytes[dataStart:dataEnd])
			if len(data) > len(prefix) && data[:len(prefix)] == prefix {
				return data[len(prefix):], true
			}
		}
		pos = dataEnd + 4
	}
	return "", false
}
