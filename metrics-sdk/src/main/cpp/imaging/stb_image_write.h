/* stb_image_write - v1.16 - public domain - http://nothings.org/stb
   writes out PNG/BMP/TGA/JPEG/HDR images to C stdio - Sean Barrett 2010-2015
   no warranty implied; use at your own risk

   For JPEG encoding only - minimal version
*/

#ifndef STB_IMAGE_WRITE_H
#define STB_IMAGE_WRITE_H

#ifdef __cplusplus
extern "C" {
#endif

typedef void stbi_write_func(void *context, void *data, int size);

int stbi_write_jpg_to_func(stbi_write_func *func, void *context, int x, int y, int comp, const void *data, int quality);

#ifdef __cplusplus
}
#endif

#ifdef STB_IMAGE_WRITE_IMPLEMENTATION

#include <stdlib.h>
#include <string.h>
#include <math.h>

#define STBIW_UCHAR(x) (unsigned char) ((x) & 0xff)

static void stbiw__putc(stbi_write_func *func, void *context, unsigned char c) {
    func(context, &c, 1);
}

static unsigned char stbiw__jpg_ZigZag[] = { 
    0,1,5,6,14,15,27,28,2,4,7,13,16,26,29,42,3,8,12,17,25,30,41,43,9,11,18,
    24,31,40,44,53,10,19,23,32,39,45,52,54,20,22,33,38,46,51,55,60,21,34,37,47,50,56,59,61,35,36,48,49,57,58,62,63 
};

static void stbiw__jpg_writeBits(stbi_write_func *func, void *context, int *bitBufP, int *bitCntP, const unsigned short *bs) {
    int bitBuf = *bitBufP, bitCnt = *bitCntP;
    bitCnt += bs[1];
    bitBuf |= bs[0] << (24 - bitCnt);
    while (bitCnt >= 8) {
        unsigned char c = (bitBuf >> 16) & 255;
        stbiw__putc(func, context, c);
        if (c == 255) stbiw__putc(func, context, 0);
        bitBuf <<= 8;
        bitCnt -= 8;
    }
    *bitBufP = bitBuf;
    *bitCntP = bitCnt;
}

static void stbiw__jpg_DCT(float *d0p, float *d1p, float *d2p, float *d3p, float *d4p, float *d5p, float *d6p, float *d7p) {
    float d0 = *d0p, d1 = *d1p, d2 = *d2p, d3 = *d3p, d4 = *d4p, d5 = *d5p, d6 = *d6p, d7 = *d7p;
    float z1, z2, z3, z4, z5, z11, z13;
    float tmp0 = d0 + d7, tmp7 = d0 - d7, tmp1 = d1 + d6, tmp6 = d1 - d6;
    float tmp2 = d2 + d5, tmp5 = d2 - d5, tmp3 = d3 + d4, tmp4 = d3 - d4;
    float tmp10 = tmp0 + tmp3, tmp13 = tmp0 - tmp3, tmp11 = tmp1 + tmp2, tmp12 = tmp1 - tmp2;
    d0 = tmp10 + tmp11; d4 = tmp10 - tmp11;
    z1 = (tmp12 + tmp13) * 0.707106781f; d2 = tmp13 + z1; d6 = tmp13 - z1;
    tmp10 = tmp4 + tmp5; tmp11 = tmp5 + tmp6; tmp12 = tmp6 + tmp7;
    z5 = (tmp10 - tmp12) * 0.382683433f; z2 = tmp10 * 0.541196100f + z5;
    z4 = tmp12 * 1.306562965f + z5; z3 = tmp11 * 0.707106781f;
    z11 = tmp7 + z3; z13 = tmp7 - z3;
    *d5p = z13 + z2; *d3p = z13 - z2; *d1p = z11 + z4; *d7p = z11 - z4;
    *d0p = d0; *d2p = d2; *d4p = d4; *d6p = d6;
}

static void stbiw__jpg_calcBits(int val, unsigned short bits[2]) {
    int tmp1 = val < 0 ? -val : val;
    val = val < 0 ? val-1 : val;
    bits[1] = 1;
    while (tmp1 >>= 1) ++bits[1];
    bits[0] = val & ((1<<bits[1])-1);
}

static int stbiw__jpg_processDU(stbi_write_func *func, void *context, int *bitBuf, int *bitCnt,
                                 float *CDU, int du_stride, float *fdtbl, int DC,
                                 const unsigned short HTDC[256][2], const unsigned short HTAC[256][2]) {
    const unsigned short EOB[2] = { HTAC[0x00][0], HTAC[0x00][1] };
    const unsigned short M16zeroes[2] = { HTAC[0xF0][0], HTAC[0xF0][1] };
    int dataOff, i, j, n, diff, end0pos, x, y;
    int DU[64];

    for (dataOff=0, n=0; n<8; ++n, dataOff+=du_stride) {
        stbiw__jpg_DCT(&CDU[dataOff], &CDU[dataOff+1], &CDU[dataOff+2], &CDU[dataOff+3],
                       &CDU[dataOff+4], &CDU[dataOff+5], &CDU[dataOff+6], &CDU[dataOff+7]);
    }
    for (dataOff=0, n=0; n<8; ++n, ++dataOff) {
        stbiw__jpg_DCT(&CDU[dataOff], &CDU[dataOff+8], &CDU[dataOff+16], &CDU[dataOff+24],
                       &CDU[dataOff+32], &CDU[dataOff+40], &CDU[dataOff+48], &CDU[dataOff+56]);
    }

    for (i=0; i<64; ++i) {
        float v = CDU[i]*fdtbl[i];
        DU[stbiw__jpg_ZigZag[i]] = (int)(v < 0 ? ceilf(v - 0.5f) : floorf(v + 0.5f));
    }

    diff = DU[0] - DC; 
    if (diff == 0) {
        stbiw__jpg_writeBits(func, context, bitBuf, bitCnt, HTDC[0]);
    } else {
        unsigned short bits[2];
        stbiw__jpg_calcBits(diff, bits);
        stbiw__jpg_writeBits(func, context, bitBuf, bitCnt, HTDC[bits[1]]);
        stbiw__jpg_writeBits(func, context, bitBuf, bitCnt, bits);
    }

    end0pos = 63;
    for (; (end0pos>0)&&(DU[end0pos]==0); --end0pos);

    if (end0pos == 0) {
        stbiw__jpg_writeBits(func, context, bitBuf, bitCnt, EOB);
        return DU[0];
    }

    for (i=1; i<=end0pos; ++i) {
        int startpos = i;
        int nrzeroes;
        for (; DU[i]==0 && i<=end0pos; ++i);
        nrzeroes = i-startpos;
        if (nrzeroes >= 16) {
            int lng = nrzeroes>>4;
            int nrmarker;
            for (nrmarker=1; nrmarker<=lng; ++nrmarker)
                stbiw__jpg_writeBits(func, context, bitBuf, bitCnt, M16zeroes);
            nrzeroes &= 15;
        }
        {
            unsigned short bits[2];
            stbiw__jpg_calcBits(DU[i], bits);
            stbiw__jpg_writeBits(func, context, bitBuf, bitCnt, HTAC[(nrzeroes<<4)+bits[1]]);
            stbiw__jpg_writeBits(func, context, bitBuf, bitCnt, bits);
        }
    }
    if (end0pos != 63) {
        stbiw__jpg_writeBits(func, context, bitBuf, bitCnt, EOB);
    }
    return DU[0];
}

// All tables as unsigned char
static unsigned char stbiw__jpg_std_dc_luminance_nrcodes[] = {0,0,1,5,1,1,1,1,1,1,0,0,0,0,0,0,0};
static unsigned char stbiw__jpg_std_dc_luminance_values[] = {0,1,2,3,4,5,6,7,8,9,10,11};
static unsigned char stbiw__jpg_std_ac_luminance_nrcodes[] = {0,0,2,1,3,3,2,4,3,5,5,4,4,0,0,1,0x7d};
static unsigned char stbiw__jpg_std_ac_luminance_values[] = {
    0x01,0x02,0x03,0x00,0x04,0x11,0x05,0x12,0x21,0x31,0x41,0x06,0x13,0x51,0x61,0x07,0x22,0x71,0x14,0x32,0x81,0x91,0xa1,0x08,
    0x23,0x42,0xb1,0xc1,0x15,0x52,0xd1,0xf0,0x24,0x33,0x62,0x72,0x82,0x09,0x0a,0x16,0x17,0x18,0x19,0x1a,0x25,0x26,0x27,0x28,
    0x29,0x2a,0x34,0x35,0x36,0x37,0x38,0x39,0x3a,0x43,0x44,0x45,0x46,0x47,0x48,0x49,0x4a,0x53,0x54,0x55,0x56,0x57,0x58,0x59,
    0x5a,0x63,0x64,0x65,0x66,0x67,0x68,0x69,0x6a,0x73,0x74,0x75,0x76,0x77,0x78,0x79,0x7a,0x83,0x84,0x85,0x86,0x87,0x88,0x89,
    0x8a,0x92,0x93,0x94,0x95,0x96,0x97,0x98,0x99,0x9a,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,0xa8,0xa9,0xaa,0xb2,0xb3,0xb4,0xb5,0xb6,
    0xb7,0xb8,0xb9,0xba,0xc2,0xc3,0xc4,0xc5,0xc6,0xc7,0xc8,0xc9,0xca,0xd2,0xd3,0xd4,0xd5,0xd6,0xd7,0xd8,0xd9,0xda,0xe1,0xe2,
    0xe3,0xe4,0xe5,0xe6,0xe7,0xe8,0xe9,0xea,0xf1,0xf2,0xf3,0xf4,0xf5,0xf6,0xf7,0xf8,0xf9,0xfa
};
static unsigned char stbiw__jpg_std_dc_chrominance_nrcodes[] = {0,0,3,1,1,1,1,1,1,1,1,1,0,0,0,0,0};
static unsigned char stbiw__jpg_std_dc_chrominance_values[] = {0,1,2,3,4,5,6,7,8,9,10,11};
static unsigned char stbiw__jpg_std_ac_chrominance_nrcodes[] = {0,0,2,1,2,4,4,3,4,7,5,4,4,0,1,2,0x77};
static unsigned char stbiw__jpg_std_ac_chrominance_values[] = {
    0x00,0x01,0x02,0x03,0x11,0x04,0x05,0x21,0x31,0x06,0x12,0x41,0x51,0x07,0x61,0x71,0x13,0x22,0x32,0x81,0x08,0x14,0x42,0x91,
    0xa1,0xb1,0xc1,0x09,0x23,0x33,0x52,0xf0,0x15,0x62,0x72,0xd1,0x0a,0x16,0x24,0x34,0xe1,0x25,0xf1,0x17,0x18,0x19,0x1a,0x26,
    0x27,0x28,0x29,0x2a,0x35,0x36,0x37,0x38,0x39,0x3a,0x43,0x44,0x45,0x46,0x47,0x48,0x49,0x4a,0x53,0x54,0x55,0x56,0x57,0x58,
    0x59,0x5a,0x63,0x64,0x65,0x66,0x67,0x68,0x69,0x6a,0x73,0x74,0x75,0x76,0x77,0x78,0x79,0x7a,0x82,0x83,0x84,0x85,0x86,0x87,
    0x88,0x89,0x8a,0x92,0x93,0x94,0x95,0x96,0x97,0x98,0x99,0x9a,0xa2,0xa3,0xa4,0xa5,0xa6,0xa7,0xa8,0xa9,0xaa,0xb2,0xb3,0xb4,
    0xb5,0xb6,0xb7,0xb8,0xb9,0xba,0xc2,0xc3,0xc4,0xc5,0xc6,0xc7,0xc8,0xc9,0xca,0xd2,0xd3,0xd4,0xd5,0xd6,0xd7,0xd8,0xd9,0xda,
    0xe2,0xe3,0xe4,0xe5,0xe6,0xe7,0xe8,0xe9,0xea,0xf2,0xf3,0xf4,0xf5,0xf6,0xf7,0xf8,0xf9,0xfa
};

static void stbiw__jpg_buildHuffmanTable(const unsigned char *nrcodes, const unsigned char *std_table, unsigned short (*htable)[2]) {
    int k = 0, code = 0;
    int j, i;
    for (j=1; j<=16; j++) {
        for (i=1; i<=(int)nrcodes[j]; i++) {
            htable[std_table[k]][0] = (unsigned short)code++;
            htable[std_table[k]][1] = (unsigned short)j;
            k++;
        }
        code *= 2;
    }
}

int stbi_write_jpg_to_func(stbi_write_func *func, void *context, int width, int height, int comp, const void *data, int quality) {
    static const unsigned char YQT[] = {16,11,10,16,24,40,51,61,12,12,14,19,26,58,60,55,14,13,16,24,40,57,69,56,14,17,22,29,51,87,80,62,18,22,37,56,68,109,103,77,24,35,55,64,81,104,113,92,49,64,78,87,103,121,120,101,72,92,95,98,112,100,103,99};
    static const unsigned char UVQT[] = {17,18,24,47,99,99,99,99,18,21,26,66,99,99,99,99,24,26,56,99,99,99,99,99,47,66,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99,99};
    static const float aasf[] = { 1.0f * 2.828427125f, 1.387039845f * 2.828427125f, 1.306562965f * 2.828427125f, 1.175875602f * 2.828427125f, 1.0f * 2.828427125f, 0.785694958f * 2.828427125f, 0.541196100f * 2.828427125f, 0.275899379f * 2.828427125f };

    int row, col, i, k, subsample;
    float fdtbl_Y[64], fdtbl_UV[64];
    unsigned short YDC_HT[256][2], UDC_HT[256][2], YAC_HT[256][2], UAC_HT[256][2];

    if (!data || !width || !height || comp > 4 || comp < 1) return 0;

    quality = quality ? quality : 90;
    subsample = quality <= 90 ? 1 : 0;
    quality = quality < 1 ? 1 : quality > 100 ? 100 : quality;
    quality = quality < 50 ? 5000 / quality : 200 - quality * 2;

    for (i = 0; i < 64; ++i) {
        int uvti, yti = (YQT[i]*quality+50)/100;
        YDC_HT[i][0] = UDC_HT[i][0] = 0;
        YDC_HT[i][1] = UDC_HT[i][1] = 0;
        yti = yti < 1 ? 1 : yti > 255 ? 255 : yti;
        uvti = (UVQT[i]*quality+50)/100;
        uvti = uvti < 1 ? 1 : uvti > 255 ? 255 : uvti;
        fdtbl_Y[stbiw__jpg_ZigZag[i]] = 1/(yti * aasf[i&7] * aasf[i>>3]);
        fdtbl_UV[stbiw__jpg_ZigZag[i]] = 1/(uvti * aasf[i&7] * aasf[i>>3]);
    }

    stbiw__jpg_buildHuffmanTable(stbiw__jpg_std_dc_luminance_nrcodes, stbiw__jpg_std_dc_luminance_values, YDC_HT);
    stbiw__jpg_buildHuffmanTable(stbiw__jpg_std_ac_luminance_nrcodes, stbiw__jpg_std_ac_luminance_values, YAC_HT);
    stbiw__jpg_buildHuffmanTable(stbiw__jpg_std_dc_chrominance_nrcodes, stbiw__jpg_std_dc_chrominance_values, UDC_HT);
    stbiw__jpg_buildHuffmanTable(stbiw__jpg_std_ac_chrominance_nrcodes, stbiw__jpg_std_ac_chrominance_values, UAC_HT);

    {
        static const unsigned char head0[] = { 0xFF,0xD8,0xFF,0xE0,0,0x10,'J','F','I','F',0,1,1,0,0,1,0,1,0,0,0xFF,0xDB,0,0x84,0 };
        static const unsigned char head2[] = { 0xFF,0xDA,0,0xC,3,1,0,2,0x11,3,0x11,0,0x3F,0 };
        const unsigned char head1[] = { 0xFF,0xC0,0,0x11,8,(unsigned char)(height>>8),STBIW_UCHAR(height),(unsigned char)(width>>8),STBIW_UCHAR(width),
            3,1,(unsigned char)(subsample?0x22:0x11),0,2,0x11,1,3,0x11,1,0xFF,0xC4,0x01,0xA2,0 };
        func(context, (void*)head0, sizeof(head0));
        for (i = 0; i < 64; ++i) func(context, (void*)(YQT+stbiw__jpg_ZigZag[i]), 1);
        func(context, (void*)"\x01", 1);
        for (i = 0; i < 64; ++i) func(context, (void*)(UVQT+stbiw__jpg_ZigZag[i]), 1);
        func(context, (void*)head1, sizeof(head1));
        func(context, (void*)(stbiw__jpg_std_dc_luminance_nrcodes+1), 16);
        func(context, (void*)stbiw__jpg_std_dc_luminance_values, 12);
        func(context, (void*)"\x10", 1);
        func(context, (void*)(stbiw__jpg_std_ac_luminance_nrcodes+1), 16);
        func(context, (void*)stbiw__jpg_std_ac_luminance_values, 162);
        func(context, (void*)"\x01", 1);
        func(context, (void*)(stbiw__jpg_std_dc_chrominance_nrcodes+1), 16);
        func(context, (void*)stbiw__jpg_std_dc_chrominance_values, 12);
        func(context, (void*)"\x11", 1);
        func(context, (void*)(stbiw__jpg_std_ac_chrominance_nrcodes+1), 16);
        func(context, (void*)stbiw__jpg_std_ac_chrominance_values, 162);
        func(context, (void*)head2, sizeof(head2));
    }

    {
        int DCY = 0, DCU = 0, DCV = 0;
        int bitBuf = 0, bitCnt = 0;
        int ofsG = comp > 2 ? 1 : 0, ofsB = comp > 2 ? 2 : 0;
        const unsigned char *dataR = (const unsigned char *)data;
        const unsigned char *dataG = dataR + ofsG;
        const unsigned char *dataB = dataR + ofsB;
        int x, y, pos;
        if (subsample) {
            for (y = 0; y < height; y += 16) {
                for (x = 0; x < width; x += 16) {
                    float Y[256], U[256], V[256];
                    for (row = y, pos = 0; row < y + 16; ++row) {
                        int clamped_row = row < height ? row : height - 1;
                        for (col = x; col < x + 16; ++col, ++pos) {
                            int clamped_col = col < width ? col : width - 1;
                            int p = (clamped_row * width + clamped_col) * comp;
                            float r = dataR[p], g = dataG[p], b = dataB[p];
                            Y[pos] = 0.299f * r + 0.587f * g + 0.114f * b - 128;
                            U[pos] = -0.16874f * r - 0.33126f * g + 0.5f * b;
                            V[pos] = 0.5f * r - 0.41869f * g - 0.08131f * b;
                        }
                    }
                    DCY = stbiw__jpg_processDU(func, context, &bitBuf, &bitCnt, Y + 0, 16, fdtbl_Y, DCY, YDC_HT, YAC_HT);
                    DCY = stbiw__jpg_processDU(func, context, &bitBuf, &bitCnt, Y + 8, 16, fdtbl_Y, DCY, YDC_HT, YAC_HT);
                    DCY = stbiw__jpg_processDU(func, context, &bitBuf, &bitCnt, Y + 128, 16, fdtbl_Y, DCY, YDC_HT, YAC_HT);
                    DCY = stbiw__jpg_processDU(func, context, &bitBuf, &bitCnt, Y + 136, 16, fdtbl_Y, DCY, YDC_HT, YAC_HT);

                    {
                        float subU[64], subV[64];
                        for (row = 0, k = 0; row < 8; ++row) {
                            for (col = 0; col < 8; ++col, ++k) {
                                int j = (row * 2) * 16 + (col * 2);
                                subU[k] = (U[j] + U[j + 1] + U[j + 16] + U[j + 17]) * 0.25f;
                                subV[k] = (V[j] + V[j + 1] + V[j + 16] + V[j + 17]) * 0.25f;
                            }
                        }
                        DCU = stbiw__jpg_processDU(func, context, &bitBuf, &bitCnt, subU, 8, fdtbl_UV, DCU, UDC_HT, UAC_HT);
                        DCV = stbiw__jpg_processDU(func, context, &bitBuf, &bitCnt, subV, 8, fdtbl_UV, DCV, UDC_HT, UAC_HT);
                    }
                }
            }
        } else {
            for (y = 0; y < height; y += 8) {
                for (x = 0; x < width; x += 8) {
                    float Y[64], U[64], V[64];
                    for (row = y, pos = 0; row < y + 8; ++row) {
                        int clamped_row = row < height ? row : height - 1;
                        for (col = x; col < x + 8; ++col, ++pos) {
                            int clamped_col = col < width ? col : width - 1;
                            int p = (clamped_row * width + clamped_col) * comp;
                            float r = dataR[p], g = dataG[p], b = dataB[p];
                            Y[pos] = 0.299f * r + 0.587f * g + 0.114f * b - 128;
                            U[pos] = -0.16874f * r - 0.33126f * g + 0.5f * b;
                            V[pos] = 0.5f * r - 0.41869f * g - 0.08131f * b;
                        }
                    }
                    DCY = stbiw__jpg_processDU(func, context, &bitBuf, &bitCnt, Y, 8, fdtbl_Y, DCY, YDC_HT, YAC_HT);
                    DCU = stbiw__jpg_processDU(func, context, &bitBuf, &bitCnt, U, 8, fdtbl_UV, DCU, UDC_HT, UAC_HT);
                    DCV = stbiw__jpg_processDU(func, context, &bitBuf, &bitCnt, V, 8, fdtbl_UV, DCV, UDC_HT, UAC_HT);
                }
            }
        }

        {
            static const unsigned short fillBits[] = {0x7F, 7};
            stbiw__jpg_writeBits(func, context, &bitBuf, &bitCnt, fillBits);
        }
    }

    func(context, (void*)"\xFF\xD9", 2);
    return 1;
}

#endif // STB_IMAGE_WRITE_IMPLEMENTATION
#endif // STB_IMAGE_WRITE_H
