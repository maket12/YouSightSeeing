package usecase_test

import (
	"context"
	"database/sql"
	"errors"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/mock"

	"YouSightSeeing/backend/internal/app/dto"
	"YouSightSeeing/backend/internal/app/uc_errors"
	"YouSightSeeing/backend/internal/app/usecase"
	"YouSightSeeing/backend/internal/domain/entity"
	"YouSightSeeing/backend/internal/domain/port/mocks"
)

/*
	===========================
	 STRUCTURE OF TEST CASE
	===========================
*/

type GoogleAuthCase struct {
	Name string

	Input   dto.GoogleAuthRequest
	WantErr error

	ExpectAccess  bool
	ExpectRefresh bool
	MockPreset    string
}

/*
	===========================
	PRESETS FOR MOCK BEHAVIOR
	===========================
*/

type MockPresetFunc func(
	u *mocks.UserRepository,
	tr *mocks.TokenRepository,
	gv *mocks.GoogleVerifier,
	tg *mocks.TokensGenerator,
)

var validSub = "google_sub_1"
var validEmail = "a@b.com"
var validClaims = &entity.GoogleClaims{
	Sub: validSub, Email: validEmail, EmailVerified: true,
}
var userID = uuid.New()

var mockPresets = map[string]MockPresetFunc{
	"EmptyToken": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
	},

	"InvalidGoogleToken": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "invalid").
			Return(nil, errors.New("invalid"))
	},

	"EmailNotVerified": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").
			Return(&entity.GoogleClaims{Sub: validSub, Email: validEmail, EmailVerified: false}, nil)
	},

	"EmptyEmail": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").
			Return(&entity.GoogleClaims{Sub: validSub, Email: "", EmailVerified: true}, nil)
	},

	"EmptySub": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").
			Return(&entity.GoogleClaims{Sub: "", Email: validEmail, EmailVerified: true}, nil)
	},

	/*
		============================
		  NEW USER FLOW
		============================
	*/

	"NewUserSuccess": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").
			Return(validClaims, nil)

		u.On("GetByGoogleSub", mock.Anything, validSub).
			Return(nil, sql.ErrNoRows)

		u.On("Create", mock.Anything, mock.Anything).
			Return(nil)

		tg.On("GenerateRefreshToken", mock.Anything, mock.Anything).
			Return("ref123", nil)

		tr.On("Create", mock.Anything, mock.Anything).
			Return(nil)

		tg.On("GenerateAccessToken", mock.Anything, mock.Anything).
			Return("acc123", nil)
	},

	"NewUserCreateFail": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").
			Return(validClaims, nil)

		u.On("GetByGoogleSub", mock.Anything, validSub).
			Return(nil, sql.ErrNoRows)

		u.On("Create", mock.Anything, mock.Anything).
			Return(errors.New("db"))
	},

	"NewUserRefreshGenFail": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").Return(validClaims, nil)
		u.On("GetByGoogleSub", mock.Anything, validSub).Return(nil, sql.ErrNoRows)
		u.On("Create", mock.Anything, mock.Anything).Return(nil)

		tg.On("GenerateRefreshToken", mock.Anything, mock.Anything).
			Return("", errors.New("fail"))
	},

	"NewUserRefreshSaveFail": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").Return(validClaims, nil)
		u.On("GetByGoogleSub", mock.Anything, validSub).Return(nil, sql.ErrNoRows)
		u.On("Create", mock.Anything, mock.Anything).Return(nil)

		tg.On("GenerateRefreshToken", mock.Anything, mock.Anything).
			Return("ref", nil)

		tr.On("Create", mock.Anything, mock.Anything).
			Return(errors.New("fail"))
	},

	"NewUserAccessGenFail": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").Return(validClaims, nil)
		u.On("GetByGoogleSub", mock.Anything, validSub).Return(nil, sql.ErrNoRows)
		u.On("Create", mock.Anything, mock.Anything).Return(nil)
		tg.On("GenerateRefreshToken", mock.Anything, mock.Anything).Return("ref", nil)
		tr.On("Create", mock.Anything, mock.Anything).Return(nil)

		tg.On("GenerateAccessToken", mock.Anything, mock.Anything).
			Return("", errors.New("fail"))
	},

	/*
		============================
		  EXISTING USER – NO TOKEN
		============================
	*/
	"ExistingNoTokenSuccess": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").Return(validClaims, nil)
		u.On("GetByGoogleSub", mock.Anything, validSub).Return(&entity.User{ID: userID, Email: validEmail}, nil)
		tr.On("GetByUserID", mock.Anything, userID).Return(nil, sql.ErrNoRows)

		tg.On("GenerateRefreshToken", mock.Anything, userID).Return("ref2", nil)
		tr.On("Create", mock.Anything, mock.Anything).Return(nil)
		tg.On("GenerateAccessToken", mock.Anything, userID).Return("acc2", nil)
	},

	"ExistingNoTokenRefreshGenFail": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").Return(validClaims, nil)
		u.On("GetByGoogleSub", mock.Anything, validSub).Return(&entity.User{ID: userID}, nil)
		tr.On("GetByUserID", mock.Anything, userID).Return(nil, sql.ErrNoRows)

		tg.On("GenerateRefreshToken", mock.Anything, userID).Return("", errors.New("fail"))
	},

	"ExistingNoTokenRefreshSaveFail": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").Return(validClaims, nil)
		u.On("GetByGoogleSub", mock.Anything, validSub).Return(&entity.User{ID: userID}, nil)
		tr.On("GetByUserID", mock.Anything, userID).Return(nil, sql.ErrNoRows)

		tg.On("GenerateRefreshToken", mock.Anything, userID).Return("ref", nil)
		tr.On("Create", mock.Anything, mock.Anything).Return(errors.New("fail"))
	},

	"ExistingNoTokenAccessFail": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").Return(validClaims, nil)
		u.On("GetByGoogleSub", mock.Anything, validSub).Return(&entity.User{ID: userID}, nil)
		tr.On("GetByUserID", mock.Anything, userID).Return(nil, sql.ErrNoRows)
		tg.On("GenerateRefreshToken", mock.Anything, userID).Return("ref", nil)
		tr.On("Create", mock.Anything, mock.Anything).Return(nil)

		tg.On("GenerateAccessToken", mock.Anything, userID).Return("", errors.New("fail"))
	},

	/*
		============================
		  EXISTING USER – ROTATION
		============================
	*/
	"ExistingTokenSuccess": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").Return(validClaims, nil)
		u.On("GetByGoogleSub", mock.Anything, validSub).
			Return(&entity.User{ID: userID}, nil)

		old := &entity.RefreshToken{
			ID: uuid.New(), UserID: userID, TokenHash: "h", ExpiresAt: time.Now().Add(time.Hour),
		}
		tr.On("GetByUserID", mock.Anything, userID).Return(old, nil)

		tr.On("Revoke", mock.Anything, "h", "new log in").Return(nil)
		tg.On("GenerateRefreshToken", mock.Anything, userID).Return("rr", nil)
		tr.On("Create", mock.Anything, mock.Anything).Return(nil)
		tg.On("GenerateAccessToken", mock.Anything, userID).Return("aa", nil)
	},

	"ExistingTokenRevokeFail": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").Return(validClaims, nil)
		u.On("GetByGoogleSub", mock.Anything, validSub).Return(&entity.User{ID: userID}, nil)
		tr.On("GetByUserID", mock.Anything, userID).Return(&entity.RefreshToken{TokenHash: "h"}, nil)

		tr.On("Revoke", mock.Anything, "h", "new log in").
			Return(errors.New("fail"))
	},

	"ExistingTokenRefreshGenFail": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").Return(validClaims, nil)
		u.On("GetByGoogleSub", mock.Anything, validSub).Return(&entity.User{ID: userID}, nil)
		tr.On("GetByUserID", mock.Anything, userID).Return(&entity.RefreshToken{TokenHash: "h"}, nil)
		tr.On("Revoke", mock.Anything, "h", "new log in").Return(nil)

		tg.On("GenerateRefreshToken", mock.Anything, userID).
			Return("", errors.New("fail"))
	},

	"ExistingTokenRefreshSaveFail": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").Return(validClaims, nil)
		u.On("GetByGoogleSub", mock.Anything, validSub).Return(&entity.User{ID: userID}, nil)
		tr.On("GetByUserID", mock.Anything, userID).Return(&entity.RefreshToken{TokenHash: "h"}, nil)
		tr.On("Revoke", mock.Anything, "h", "new log in").Return(nil)
		tg.On("GenerateRefreshToken", mock.Anything, userID).Return("ok", nil)

		tr.On("Create", mock.Anything, mock.Anything).
			Return(errors.New("fail"))
	},

	"ExistingTokenGetFail": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").
			Return(validClaims, nil)

		u.On("GetByGoogleSub", mock.Anything, validSub).
			Return(&entity.User{ID: userID}, nil)

		tr.On("GetByUserID", mock.Anything, userID).
			Return(nil, errors.New("db fail")) // <-- not sql.ErrNoRows
	},

	"HappyFullSuccess": func(u *mocks.UserRepository, tr *mocks.TokenRepository, gv *mocks.GoogleVerifier, tg *mocks.TokensGenerator) {
		gv.On("VerifyToken", mock.Anything, "token").
			Return(validClaims, nil)

		u.On("GetByGoogleSub", mock.Anything, validSub).
			Return(&entity.User{ID: userID, Email: validEmail}, nil)

		old := &entity.RefreshToken{
			ID:        uuid.New(),
			UserID:    userID,
			TokenHash: "old_hash",
			ExpiresAt: time.Now().Add(time.Hour),
		}

		tr.On("GetByUserID", mock.Anything, userID).
			Return(old, nil)

		tr.On("Revoke", mock.Anything, "old_hash", "new log in").
			Return(nil)

		tg.On("GenerateRefreshToken", mock.Anything, userID).
			Return("new_ref_token", nil)

		tr.On("Create", mock.Anything, mock.Anything).
			Return(nil)

		tg.On("GenerateAccessToken", mock.Anything, userID).
			Return("new_access_token", nil)
	},
}

/*
	===========================
	   TABLE OF TEST CASES
	===========================
*/

var cases = []GoogleAuthCase{
	{"Empty token", dto.GoogleAuthRequest{GoogleToken: ""}, uc_errors.EmptyGoogleTokenError, false, false, "EmptyToken"},
	{"Invalid token", dto.GoogleAuthRequest{GoogleToken: "invalid"}, uc_errors.GoogleTokenValidationError, false, false, "InvalidGoogleToken"},

	{"Email not verified", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.EmailNotVerifiedError, false, false, "EmailNotVerified"},
	{"Empty email", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.EmptyEmailError, false, false, "EmptyEmail"},
	{"Empty sub", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.EmptyGoogleSubError, false, false, "EmptySub"},

	{"New user - success", dto.GoogleAuthRequest{GoogleToken: "token"}, nil, true, true, "NewUserSuccess"},
	{"New user - create fail", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.CreateUserError, false, false, "NewUserCreateFail"},
	{"New user - refresh gen fail", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.GenerateRefreshTokenError, false, false, "NewUserRefreshGenFail"},
	{"New user - refresh save fail", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.CreateRefreshTokenError, false, false, "NewUserRefreshSaveFail"},
	{"New user - access fail", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.GenerateAccessTokenError, false, false, "NewUserAccessGenFail"},

	{"Existing no token - success", dto.GoogleAuthRequest{GoogleToken: "token"}, nil, true, true, "ExistingNoTokenSuccess"},
	{"Existing no token - refresh gen fail", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.GenerateRefreshTokenError, false, false, "ExistingNoTokenRefreshGenFail"},
	{"Existing no token - refresh save fail", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.CreateRefreshTokenError, false, false, "ExistingNoTokenRefreshSaveFail"},
	{"Existing no token - access fail", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.GenerateAccessTokenError, false, false, "ExistingNoTokenAccessFail"},

	{"Existing token - success", dto.GoogleAuthRequest{GoogleToken: "token"}, nil, true, true, "ExistingTokenSuccess"},
	{"Existing token - revoke fail", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.RevokeRefreshTokenError, false, false, "ExistingTokenRevokeFail"},
	{"Existing token - refresh gen fail", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.GenerateRefreshTokenError, false, false, "ExistingTokenRefreshGenFail"},
	{"Existing token - refresh save fail", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.CreateRefreshTokenError, false, false, "ExistingTokenRefreshSaveFail"},

	{"Existing user - GetByUserID DB error", dto.GoogleAuthRequest{GoogleToken: "token"}, uc_errors.GetRefreshTokenByUserIDError, false, false, "ExistingTokenGetFail"},
	{"Happy path: full success scenario", dto.GoogleAuthRequest{GoogleToken: "token"}, nil, true, true, "HappyFullSuccess"},
}

/*
	===========================
	     TEST RUNNER
	===========================
*/

func TestGoogleAuthUC_TableDriven(t *testing.T) {
	for _, tt := range cases {
		t.Run(tt.Name, func(t *testing.T) {
			uRepo := mocks.NewUserRepository(t)
			tRepo := mocks.NewTokenRepository(t)
			gVerf := mocks.NewGoogleVerifier(t)
			tGen := mocks.NewTokensGenerator(t)

			mockPresets[tt.MockPreset](uRepo, tRepo, gVerf, tGen)

			uc := &usecase.GoogleAuthUC{
				Users:           uRepo,
				RefreshTokens:   tRepo,
				GoogleVerf:      gVerf,
				TokensGenerator: tGen,
			}

			resp, err := uc.Execute(context.Background(), tt.Input)

			if tt.WantErr != nil {
				assert.Error(t, err)
				assert.True(t, errors.Is(err, tt.WantErr))
				return
			}

			assert.NoError(t, err)

			if tt.ExpectAccess {
				assert.NotEmpty(t, resp.AccessToken)
			}
			if tt.ExpectRefresh {
				assert.NotEmpty(t, resp.RefreshToken)
			}
		})
	}
}
