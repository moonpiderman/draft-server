package com.wafflestudio.draft.api

import com.wafflestudio.draft.dto.*
import com.wafflestudio.draft.dto.request.*
import com.wafflestudio.draft.dto.response.*
import com.wafflestudio.draft.model.Device
import com.wafflestudio.draft.model.Room
import com.wafflestudio.draft.model.User
import com.wafflestudio.draft.security.CurrentUser
import com.wafflestudio.draft.security.JwtTokenProvider
import com.wafflestudio.draft.security.oauth2.AuthUserService
import com.wafflestudio.draft.security.oauth2.OAuth2Provider
import com.wafflestudio.draft.security.oauth2.client.OAuth2Response
import com.wafflestudio.draft.security.password.UserPrincipal
import com.wafflestudio.draft.service.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.io.IOException
import javax.servlet.http.HttpServletResponse
import javax.validation.Valid

@RestController
@RequestMapping("/api/v1/user")
class UserApiController(private val oAuth2Provider: OAuth2Provider,
                        private val authUserService: AuthUserService,
                        private val userService: UserService,
                        private val passwordEncoder: PasswordEncoder,
                        private val regionService: RegionService,
                        private val preferenceService: PreferenceService,
                        private val deviceService: DeviceService,
                        private val roomService: RoomService,
                        private val jwtTokenProvider: JwtTokenProvider) {
    @PostMapping("/signup/")
    @Throws(IOException::class)
    fun createUser(@ModelAttribute @Valid signUpRequest: SignUpDTO.Request, response: HttpServletResponse): ResponseEntity<User?>? {
        val user: User?
        val username: String = signUpRequest.username
        if (userService.existsUserByUsername(username)) {
            throw ResponseStatusException(HttpStatus.CONFLICT)
        }
        when (signUpRequest.grantType) {
            "OAUTH" -> try {
                val oAuth2Response: OAuth2Response = oAuth2Provider.requestAuthentication(signUpRequest)
                user = User(username, oAuth2Response.email)
            } catch (e: Exception) {
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, e.message)
                return null
            }
            "PASSWORD" -> {
                if (signUpRequest.email == null || signUpRequest.password == null) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST)
                    return null
                }
                user = User(username, signUpRequest.email!!)
                user.password = passwordEncoder.encode(signUpRequest.password)
            }
            else -> {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Grant type not valid")
                return null
            }
        }
        authUserService.saveUser(user)
        response.addHeader("Authentication", jwtTokenProvider.generateToken(user.email))
        return ResponseEntity(user, HttpStatus.CREATED)
    }

    @GetMapping("/check-username/")
    fun checkUsername(@RequestBody @Valid checkUsernameRequest: UserDTO.CheckUsernameRequest): ResponseEntity<Void?>? {
        if (userService.existsUserByUsername(checkUsernameRequest.username)) {
            throw ResponseStatusException(HttpStatus.CONFLICT)
        }
        return ResponseEntity(HttpStatus.OK)
    }

    //    @PreAuthorize("hasRole('ROLE_USER')")
    @GetMapping("/me/")
    fun myInfo(@CurrentUser currentUser: UserPrincipal): ResponseEntity<UserDTO.UserInformationResponse> {
        return ResponseEntity(UserDTO.UserInformationResponse(currentUser.user), HttpStatus.OK)
    }

    //    @PreAuthorize("hasRole('ROLE_USER')")
    @PostMapping("/info/")
    fun setPreferences(@RequestBody @Valid preferenceRequestList: List<PreferenceDTO.SetPreferenceRequest>, @CurrentUser currentUser: UserPrincipal): ListResponse<PreferenceDTO.PreferenceInRegionResponse> {
        return ListResponse(preferenceRequestList.map { preferenceRequest ->
            val region = regionService.findRegionById(preferenceRequest.regionId)
            if (region.isEmpty) {
                // FIXME: when a region is not found, we should not apply whole preferences of the request
                throw ResponseStatusException(HttpStatus.NOT_FOUND)
            }
            val preferences = preferenceRequest.preferences
            preferenceService.setPreferences(currentUser.user, region.get(), preferences)
            PreferenceDTO.PreferenceInRegionResponse(region.get(), preferences)
        })
    }

    @GetMapping("/playable/")
    fun getPlayableUsers(@Valid @ModelAttribute getUsersByPreferenceRequest: PreferenceDTO.GetUsersByPreferenceRequest): ListResponse<Long?>? {
        val regionName = getUsersByPreferenceRequest.regionName
        val dayOfWeek = getUsersByPreferenceRequest.dayOfWeek
        val startTime = getUsersByPreferenceRequest.startTime
        val endTime = getUsersByPreferenceRequest.endTime
        return ListResponse(preferenceService.getPlayableUsers(regionName, dayOfWeek, startTime, endTime))
    }

    @PostMapping("/device/")
    @ResponseStatus(HttpStatus.CREATED)
    fun setDevice(@RequestBody @Valid request: DeviceDTO.SetDeviceRequest, @CurrentUser currentUser: UserPrincipal): DeviceDTO.Response {
        val device = Device(request.deviceToken)
        device.user = currentUser.user
        deviceService.create(device)
        return DeviceDTO.Response(device)
    }

    @GetMapping("/room/")
    fun getBelongingRooms(@CurrentUser currentUser: UserPrincipal): RoomDTO.RoomsOfUserResponse? {
        val rooms: List<Room>? = roomService.findRoomsByUser(currentUser.user)
        val roomsOfUserResponse = RoomDTO.RoomsOfUserResponse(currentUser.user)
        roomsOfUserResponse.rooms = rooms?.map { RoomDTO.Response(it) } ?: emptyList()
        return roomsOfUserResponse
    }
}
